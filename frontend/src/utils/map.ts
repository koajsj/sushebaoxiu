import type { MapMarker, ProjectedMarker } from '../types/dispatch'

export function validLocation(longitude:number|null,latitude:number|null):boolean {
  return longitude!==null&&latitude!==null&&Number.isFinite(longitude)&&Number.isFinite(latitude)
    &&longitude>=-180&&longitude<=180&&latitude>=-90&&latitude<=90
}
export function findMarker(markers:MapMarker[],key:string):MapMarker|null {
  return markers.find(marker=>marker.key===key)||null
}
/** Local equirectangular projection; this is a static campus map, without online tiles. */
export function projectMarkers(markers:MapMarker[]):ProjectedMarker[] {
  const valid=markers.filter(marker=>validLocation(marker.longitude,marker.latitude))
  if(!valid.length) return []
  const latitudes=valid.map(m=>m.latitude!),longitudes=valid.map(m=>m.longitude!)
  const centerLat=(Math.min(...latitudes)+Math.max(...latitudes))/2
  const centerLon=(Math.min(...longitudes)+Math.max(...longitudes))/2
  const cosine=Math.max(.000001,Math.cos(centerLat*Math.PI/180))
  const xy=valid.map(m=>({marker:m,x:(m.longitude!-centerLon)*cosine,y:-(m.latitude!-centerLat)}))
  const rangeX=Math.max(...xy.map(p=>p.x))-Math.min(...xy.map(p=>p.x))
  const rangeY=Math.max(...xy.map(p=>p.y))-Math.min(...xy.map(p=>p.y))
  const scale=Math.min(rangeX===0?Infinity:800/rangeX,rangeY===0?Infinity:440/rangeY)
  const safeScale=Number.isFinite(scale)?scale:1
  const groups=new Map<string,ProjectedMarker[]>()
  for(const point of xy) {
    const marker={...point.marker,x:500+point.x*safeScale,y:320+point.y*safeScale,
      anchorX:500+point.x*safeScale,anchorY:320+point.y*safeScale}
    const key=`${point.marker.longitude!.toFixed(7)},${point.marker.latitude!.toFixed(7)}`
    const group=groups.get(key)||[];group.push(marker);groups.set(key,group)
  }
  // Expand co-located markers on a grid; leader lines preserve the exact geographic anchor.
  for(const group of groups.values()) {
    if(group.length<2) continue
    const columns=Math.ceil(Math.sqrt(group.length)),rows=Math.ceil(group.length/columns),spacing=26
    const width=(columns-1)*spacing,height=(rows-1)*spacing
    const centerX=Math.min(970-width/2,Math.max(30+width/2,group[0]!.anchorX))
    const centerY=Math.min(610-height/2,Math.max(30+height/2,group[0]!.anchorY))
    group.forEach((m,index)=>{m.x=centerX-width/2+(index%columns)*spacing;m.y=centerY-height/2+Math.floor(index/columns)*spacing})
  }
  return [...groups.values()].flat()
}
export function constrainPan(x:number,y:number,zoom:number):{x:number;y:number} {
  if(zoom<=1)return {x:0,y:0}
  const dx=500*(zoom-1),dy=320*(zoom-1)
  return {x:Math.max(-dx,Math.min(dx,x)),y:Math.max(-dy,Math.min(dy,y))}
}
