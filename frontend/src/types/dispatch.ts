import type { OrderStatus } from './repair'

export interface WorkerRecommendation {
  recommendationId:number; workerId:number; workerName:string; skillType:string
  activeTaskCount:number; completedTaskCount:number; rating:number; distanceKm:number|null
  skillScore:number; distanceScore:number; loadScore:number; ratingScore:number; totalScore:number; reason:string
}
export interface CampusBuilding { id:number; name:string; type:string; longitude:number|null; latitude:number|null }
export interface MapOrder { id:number; title:string; status:OrderStatus; address:string; typeName:string; workerId:number|null; longitude:number|null; latitude:number|null; createTime:string }
export interface MapWorker { id:number; name:string; skillType:string; status:'AVAILABLE'|'BUSY'; activeTaskCount:number; longitude:number|null; latitude:number|null }
export interface MapMarker {
  key:string; kind:'building'|'order'|'worker'; title:string; longitude:number|null; latitude:number|null
  building?:CampusBuilding; order?:MapOrder; worker?:MapWorker
}
export interface ProjectedMarker extends MapMarker { x:number; y:number; anchorX:number; anchorY:number }
