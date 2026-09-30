<script setup lang="ts">
defineProps<{title:string;description:string}>()
</script>
<template><section class="chart-panel"><header><div><h2>{{ title }}</h2><p>{{ description }}</p></div><slot name="action" /></header><div class="chart-body"><slot /></div></section></template>
