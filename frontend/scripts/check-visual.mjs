import assert from 'node:assert/strict'
import { createSSRApp, h } from 'vue'
import { renderToString } from 'vue/server-renderer'
import { createServer } from 'vite'

// Presentation-only acceptance, without browser or changes to business data.
const server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
try {
  const { default: BrandMark } = await server.ssrLoadModule('/src/components/BrandMark.vue')
  const brand = await renderToString(createSSRApp(BrandMark))
  assert.match(brand, /<img[^>]*brand-icon/,'Official artwork is used for the brand')
  assert.ok(!brand.includes('>C<'), 'Brand must not use a letter placeholder')
  const { default: Hero } = await server.ssrLoadModule('/src/components/CampusHero.vue')
  for (const [asset, file] of [['entrance', 'plaza'], ['courtyard', 'dormitory'], ['worker', 'maintenance'], ['service', 'service-space']]) {
    const html = await renderToString(createSSRApp({ render: () => h(Hero, { asset, label: '校园欢迎区域' }, { default: () => '欢迎回来' }) }))
    assert.match(html, new RegExp(`${file}-1440.webp`), `${asset}: correct provided photograph`)
    assert.match(html, new RegExp(`${file}-768.webp`), `${asset}: responsive photograph`)
    assert.ok(html.includes('欢迎回来'), 'Content stays available independently of the image')
    const Fallback = { ...Hero, setup(props, context) {
      const state = Hero.setup(props, context); state.failed.value = true; return state
    } }
    const fallback = await renderToString(createSSRApp({ render: () => h(Fallback, { asset, label: '校园欢迎区域' }, { default: () => '欢迎回来' }) }))
    assert.ok(!fallback.includes('<picture'), 'Failed photo is removed')
    assert.ok(fallback.includes('欢迎回来') && fallback.includes('campus-hero--fallback'), 'Readable text and fallback surface remain')
  }
  const { default: Icon } = await server.ssrLoadModule('/src/components/BrandIcon.vue')
  const FallbackIcon = { ...Icon, setup(props, context) {
    const state = Icon.setup(props, context); state.failed.value = true; return state
  } }
  const fallback = await renderToString(createSSRApp(FallbackIcon))
  assert.ok(fallback.includes('<svg') && !fallback.includes('<img'), 'Failed brand artwork has a vector fallback')
  assert.ok(!fallback.includes('>C<'))
  console.log('PASS official logo, four distinct provided photos, responsive sources and readable image-error fallbacks')
} finally { await server.close() }
