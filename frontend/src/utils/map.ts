import type { CampusBuilding, MapOrder, MapWorker, MapMarker, ProjectedMarker } from '../types/dispatch'

/** Keep the same marker identity and source data across the map and Dashboard. */
export function createMapMarkers(buildings:CampusBuilding[],orders:MapOrder[],workers:MapWorker[]):MapMarker[] {
  return [
    ...buildings.map(building=>({key:`building-${building.id}`,kind:'building' as const,title:building.name,longitude:building.longitude,latitude:building.latitude,building})),
    ...orders.map(order=>({key:`order-${order.id}`,kind:'order' as const,title:order.title,longitude:order.longitude,latitude:order.latitude,order})),
    ...workers.map(worker=>({key:`worker-${worker.id}`,kind:'worker' as const,title:worker.name,longitude:worker.longitude,latitude:worker.latitude,worker})),
  ]
}

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
    const columns=Math.ceil(Math.sqrt(group.length)),rows=Math.ceil(group.length/columns),spacing=38
    const width=(columns-1)*spacing,height=(rows-1)*spacing
    const centerX=Math.min(970-width/2,Math.max(30+width/2,group[0]!.anchorX))
    const centerY=Math.min(610-height/2,Math.max(30+height/2,group[0]!.anchorY))
    group.forEach((m,index)=>{m.x=centerX-width/2+(index%columns)*spacing;m.y=centerY-height/2+Math.floor(index/columns)*spacing})
  }
  return [...groups.values()].flat()
}
interface MarkerLabel { key:string; text:string; x:number; y:number; width:number; height:number }
interface LabelObstacle { x:number; y:number; width:number; height:number }
/** Keep names clear of markers and other names; the active marker takes priority. */
export function layoutMarkerLabels(points:ProjectedMarker[],activeKey:string|null=null,obstacles:LabelObstacle[]=[]):MarkerLabel[] {
  const labels:MarkerLabel[]=[]
  const priority={building:0,worker:1,order:2}
  const sorted=[...points].sort((a,b)=>Number(b.key===activeKey)-Number(a.key===activeKey)||priority[a.kind]-priority[b.kind])
  const overlaps=(a:{x:number;y:number;width:number;height:number},b:{x:number;y:number;width:number;height:number})=>
    a.x<b.x+b.width+4&&a.x+a.width+4>b.x&&a.y<b.y+b.height+4&&a.y+a.height+4>b.y
  for(const point of sorted) {
    const title=point.title.trim()||{building:'未命名建筑',worker:'维修员',order:'报修点'}[point.kind]
    const chars=Array.from(title),text=chars.length>12?`${chars.slice(0,11).join('')}…`:title
    const width=Array.from(text).reduce((size,char)=>size+(/^[\x00-\x7f]$/.test(char)?9:16),20),height=28
    const candidates=[
      {x:point.x-width/2,y:point.y+22}, {x:point.x-width/2,y:point.y-22-height},
      {x:point.x+22,y:point.y-height/2}, {x:point.x-22-width,y:point.y-height/2},
    ].map(position=>({key:point.key,text,x:Math.max(4,Math.min(996-width,position.x)),y:Math.max(4,Math.min(636-height,position.y)),width,height}))
    const clear=candidates.find(label=>!labels.some(other=>overlaps(label,other))&&!obstacles.some(other=>overlaps(label,other))&&!points.some(other=>overlaps(label,{x:other.x-17,y:other.y-17,width:34,height:34})))
    const label=clear||(point.key===activeKey?candidates[0]:undefined)
    if(label)labels.push(label)
  }
  // Paint the active name last, so it remains readable even in a dense cluster.
  return labels.sort((a,b)=>Number(a.key===activeKey)-Number(b.key===activeKey))
}
export function constrainPan(x:number,y:number,zoom:number):{x:number;y:number} {
  if(zoom<=1)return {x:0,y:0}
  const dx=500*(zoom-1),dy=320*(zoom-1)
  return {x:Math.max(-dx,Math.min(dx,x)),y:Math.max(-dy,Math.min(dy,y))}
}
