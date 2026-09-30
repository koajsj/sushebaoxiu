import assert from 'node:assert/strict'
import { createServer } from 'vite'
const server=await createServer({server:{middlewareMode:true},appType:'custom'})
try {
 const { projectMarkers, validLocation, findMarker, constrainPan }=await server.ssrLoadModule('/src/utils/map.ts')
 assert.equal(validLocation(0,0),true)
 assert.equal(validLocation(null,0),false)
 assert.equal(validLocation(181,0),false)
 const markers=[{key:'b1',kind:'building',longitude:116.31,latitude:39.99,title:'宿舍'},
 {key:'o1',kind:'order',longitude:116.31,latitude:39.99,title:'灯具'},
 {key:'o2',kind:'order',longitude:116.31,latitude:39.99,title:'水管'},
 {key:'w1',kind:'worker',longitude:116.315,latitude:39.995,title:'电工'},
 {key:'unknown',kind:'worker',longitude:null,latitude:null,title:'未配置'}]
 const points=projectMarkers(markers)
 assert.equal(points.length,4)
 assert.equal(new Set(points.map(p=>`${p.x},${p.y}`)).size,4,'overlapping markers can each be selected')
 assert.ok(points.every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)&&p.x>=30&&p.x<=970&&p.y>=30&&p.y<=610))
 const west=points.find(p=>p.key==='b1'),east=points.find(p=>p.key==='w1')
 assert.ok(east.anchorX>west.anchorX && east.anchorY<west.anchorY,'east is right and north is up')
 assert.equal(findMarker(markers,'o2').title,'水管','click/keyboard selection resolves matching detail')
 assert.equal(findMarker(markers,'missing'),null)
 assert.deepEqual(projectMarkers([]),[])
 assert.ok(projectMarkers([markers[0]]).every(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)))
 assert.deepEqual(constrainPan(10000,-10000,1),{x:0,y:0})
 assert.deepEqual(constrainPan(10000,-10000,2),{x:500,y:-320})
 console.log('PASS map coordinate validity, projection, overlap, selection, empty/single input and pan bounds')
} finally {await server.close()}
