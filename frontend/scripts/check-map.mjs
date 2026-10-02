import assert from 'node:assert/strict'
import { createServer } from 'vite'
import { createSSRApp } from 'vue'
import { renderToString } from 'vue/server-renderer'
const server=await createServer({server:{middlewareMode:true,hmr:false,ws:false},appType:'custom'})
try {
 const { createMapMarkers, projectMarkers, validLocation, findMarker, constrainPan }=await server.ssrLoadModule('/src/utils/map.ts')
 const building={id:1,name:'学生宿舍1号楼',type:'DORMITORY',longitude:116.31,latitude:39.99}
 const order={id:1,title:'水管漏水',status:'PROCESSING',address:'学生宿舍1号楼 301',typeName:'水电维修',workerId:1,longitude:null,latitude:null,createTime:'2026-10-01T09:00:00'}
 const worker={id:1,name:'王师傅',skillType:'水电维修',status:'BUSY',activeTaskCount:1,longitude:116.31,latitude:39.99}
 const sharedMarkers=createMapMarkers([building],[order],[worker])
 assert.deepEqual(sharedMarkers.map(marker=>marker.key),['building-1','order-1','worker-1'],'Different resource types preserve independent selection keys even with the same ID')
 assert.equal(findMarker(sharedMarkers,'building-1').building,building)
 assert.equal(findMarker(sharedMarkers,'order-1').order,order)
 assert.equal(findMarker(sharedMarkers,'worker-1').worker,worker)
 assert.equal(findMarker(sharedMarkers,'order-1').longitude,null,'Missing locations stay available for the location list')
 assert.deepEqual(createMapMarkers([],[],[]),[])
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
 const { campusLandmarks, projectCampusMarkers }=await server.ssrLoadModule('/src/utils/campusMap.ts')
 const campusBuilding={key:'building-1',kind:'building',title:'学生宿舍1号楼',longitude:116.31,latitude:39.99,building:{id:1,name:'学生宿舍1号楼',type:'DORMITORY',longitude:116.31,latitude:39.99}}
 const campusOrder={key:'order-1',kind:'order',title:'水管漏水',longitude:null,latitude:null,order:{address:'学生宿舍1号楼 301'}}
 const campusWorker={key:'worker-1',kind:'worker',title:'电工',longitude:116.31,latitude:39.99}
 const schematic=projectCampusMarkers([campusBuilding,campusOrder,campusWorker],[campusBuilding])
 assert.equal(schematic.length,3,'Named orders do not require geographic coordinates')
 assert.ok(schematic.every(point=>point.anchorX===campusLandmarks.find(p=>p.id==='dorm-1').x))
 assert.equal(projectCampusMarkers([{...campusOrder,order:{address:'未收录楼栋 301'}}],[campusBuilding]).length,0,'Unknown sites must not be invented')
 assert.equal(projectCampusMarkers([{...campusWorker,longitude:120}],[campusBuilding]).length,0,'Distant workers are not placed at a campus building')
 assert.deepEqual(projectCampusMarkers([campusOrder],[]),projectCampusMarkers([campusOrder],[campusBuilding]),'Named positions stay stable across layers')
 const { default: MapPanel }=await server.ssrLoadModule('/src/components/MapPanel.vue')
 const html=await renderToString(createSSRApp(MapPanel,{markers:[campusBuilding,campusOrder,campusWorker],locations:[campusBuilding],selectedKey:'worker-1'}))
 const labels=html.match(/<text[^>]*class="map-marker-label"[^>]*>[\s\S]*?<\/text>/g)||[]
 assert.ok(labels.some(label=>label.includes('电工')),'Worker name is visible on the map, not just a person glyph')
 assert.ok(labels.some(label=>label.includes('宿舍')),'Building name is visible on the map')
 assert.ok(html.includes('campus-plan-v1.jpg'),'Generated illustration is used as the map base')
 assert.ok(html.includes('第一食堂')&&html.includes('教学楼A栋')&&html.includes('学生宿舍3号楼'),'Chinese landmarks are real text, not baked image text')
 assert.ok(!html.includes('116.31')&&!html.includes('39.99'),'Geographic addresses are not exposed as map labels')
 const compactHtml=await renderToString(createSSRApp(MapPanel,{markers:[],selectedKey:null,compact:true}))
 assert.ok(compactHtml.includes('宿舍1栋')&&compactHtml.includes('font-size:26px'),'Dashboard keeps concise, larger landmark names')
 assert.ok(!html.includes('marker-glyph'),'Marker icons do not depend on font glyph availability')
 const { layoutMarkerLabels }=await server.ssrLoadModule('/src/utils/map.ts')
 const crowded=projectMarkers(Array.from({length:36},(_,i)=>({...markers[0],key:`crowded-${i}`,title:`校园楼栋${i}`})))
 const crowdedLabels=layoutMarkerLabels(crowded)
 const overlaps=(a,b)=>a.x<b.x+b.width&&a.x+a.width>b.x&&a.y<b.y+b.height&&a.y+a.height>b.y
 assert.ok(crowdedLabels.length>0)
 assert.ok(crowdedLabels.every(label=>label.x>=0&&label.y>=0&&label.x+label.width<=1000&&label.y+label.height<=640),'Labels stay within the map')
 assert.ok(crowdedLabels.every((label,i)=>crowdedLabels.slice(i+1).every(other=>!overlaps(label,other))),'Crowded labels never overlap')
 const hidden=crowded.find(point=>!crowdedLabels.some(label=>label.key===point.key))
 assert.ok(hidden,'Crowded labels are reduced instead of stacked')
 assert.ok(layoutMarkerLabels(crowded,hidden.key).some(label=>label.key===hidden.key),'Selected or focused crowded marker always exposes its name')
 assert.deepEqual(layoutMarkerLabels([]),[])
 const many=projectCampusMarkers(Array.from({length:100},(_,i)=>({...campusOrder,key:`order-${i}`})),[campusBuilding])
 assert.ok(many.every(point=>point.x>=20&&point.x<=980&&point.y>=20&&point.y<=620),'Dense schematic markers remain inside the map')
 assert.equal(new Set(many.map(point=>`${point.x},${point.y}`)).size,100)
 assert.deepEqual(layoutMarkerLabels([points[0]],null,[{x:0,y:0,width:1000,height:640}]),[],'Landmark labels reserve space against passive task labels')
 console.log('PASS fictional landmark placement, named orders without coordinates, missing locations, static worker association and dense bounds')
 console.log('PASS vector marker rendering, visible names, label bounds, collision avoidance and active label priority')
 console.log('PASS map coordinate validity, projection, overlap, selection, empty/single input and pan bounds')
 console.log('PASS shared map markers preserve selection identity, source details and missing locations')
} finally {await server.close()}
