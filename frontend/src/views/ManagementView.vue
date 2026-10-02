<script setup lang="ts">
import { onMounted,reactive,ref,watch } from 'vue'
import { useRouter } from 'vue-router'
import { getCatalog } from '../api/repair'
import { createAccount,getAccounts,saveBuilding,saveRepairType,setAccountStatus,setWorkerStatus,updateAccount,type Account,type AccountForm,type BuildingForm,type RepairTypeForm } from '../api/manage'
import { useAuthStore } from '../store/auth'
import type { UserRole } from '../types'
import type { Catalog } from '../types/repair'
const router=useRouter(),auth=useAuthStore()
const tab=ref<'users'|'buildings'|'types'>('users'),role=ref<UserRole>('STUDENT'),page=ref(1)
const tabs=[{key:'users',label:'账号与人员'},{key:'buildings',label:'楼栋坐标'},{key:'types',label:'故障类型'}] as const
const accounts=ref<Account[]>([]),total=ref(0),catalog=ref<Catalog>({types:[],buildings:[]})
const loading=ref(false),busy=ref(false),error=ref(''),feedback=ref(''),editingId=ref<number|null>(null)
const blank=():AccountForm=>({role:'STUDENT',username:'',password:'',realName:'',phone:'',studentNo:'',college:'',className:'',buildingId:null,roomNo:'',skillType:'',longitude:null,latitude:null})
const form=reactive<AccountForm>(blank())
const buildingId=ref<number|null>(null),building=reactive<BuildingForm>({name:'',type:'宿舍',longitude:null,latitude:null})
const typeId=ref<number|null>(null),type=reactive<RepairTypeForm>({name:'',description:''})
let revision=0
function numberOrNull(value:unknown){if(value===null||value==='')return null;const number=Number(value);if(!Number.isFinite(number))throw new Error('坐标格式不正确');return number}
async function loadAccounts(){const current=++revision;loading.value=true;error.value=''
  try{const result=await getAccounts(role.value,page.value);if(current!==revision)return;accounts.value=result.records;total.value=result.total}
  catch(e){if(current===revision)error.value=e instanceof Error?e.message:'账号加载失败'}finally{if(current===revision)loading.value=false}
}
async function loadCatalog(){try{catalog.value=await getCatalog()}catch(e){error.value=e instanceof Error?e.message:'校园资料加载失败'}}
onMounted(()=>{void loadAccounts();void loadCatalog()})
watch(role,()=>{page.value=1;resetForm();void loadAccounts()})
function resetForm(){editingId.value=null;Object.assign(form,blank(),{role:role.value==='WORKER'?'WORKER':'STUDENT'});error.value='';feedback.value=''}
function editAccount(item:Account){editingId.value=item.id;Object.assign(form,{role:item.role,username:item.username,password:'',realName:item.realName,phone:item.phone||'',studentNo:item.studentNo||'',college:item.college||'',className:item.className||'',buildingId:item.buildingId,roomNo:item.roomNo||'',skillType:item.skillType||'',longitude:item.longitude,latitude:item.latitude});error.value='';feedback.value=''}
async function submitAccount(){if(busy.value)return;busy.value=true;error.value='';feedback.value=''
  const savedId=editingId.value,snapshot=JSON.stringify(form)
  try{const payload={...form,longitude:numberOrNull(form.longitude),latitude:numberOrNull(form.latitude)}
    if(savedId){const {role:unusedRole,username:unusedUsername,password:unusedPassword,...update}=payload;void unusedRole;void unusedUsername;void unusedPassword;await updateAccount(savedId,update)}
    else await createAccount(payload);const message=savedId?'账号资料已保存':'账号已创建'
    if(editingId.value===savedId&&JSON.stringify(form)===snapshot)resetForm();feedback.value=message;await loadAccounts()
  }catch(e){error.value=e instanceof Error?e.message:'保存失败，输入已保留'}finally{busy.value=false}
}
async function toggleAccount(item:Account){const next=item.status?0:1
  if(next===0&&!window.confirm(`停用账号 ${item.username}？该用户将立即退出登录。`))return
  busy.value=true;error.value='';try{await setAccountStatus(item.id,next);if(item.id===auth.user?.id&&next===0){auth.clearSession();await router.replace('/login');return}await loadAccounts()}
  catch(e){error.value=e instanceof Error?e.message:'账号状态修改失败'}finally{busy.value=false}
}
async function toggleWorker(item:Account){if(!item.workerId)return;const next=item.workerStatus?0:1
  if(next===0&&!window.confirm(`将 ${item.realName} 设为暂不可接单？在途任务需要先收回重派。`))return
  busy.value=true;error.value='';try{await setWorkerStatus(item.workerId,next);await loadAccounts()}
  catch(e){error.value=e instanceof Error?e.message:'工作状态修改失败'}finally{busy.value=false}
}
function editBuilding(item:Catalog['buildings'][number]){buildingId.value=item.id;Object.assign(building,{name:item.name,type:item.type,longitude:item.longitude,latitude:item.latitude})}
function editType(item:Catalog['types'][number]){typeId.value=item.id;Object.assign(type,{name:item.name,description:item.description})}
async function submitCatalog(kind:'building'|'type'){if(busy.value)return;busy.value=true;error.value='';feedback.value=''
  const savedId=kind==='building'?buildingId.value:typeId.value,snapshot=JSON.stringify(kind==='building'?building:type)
  try{if(kind==='building'){await saveBuilding(savedId,{...building,longitude:numberOrNull(building.longitude),latitude:numberOrNull(building.latitude)})
      if(buildingId.value===savedId&&JSON.stringify(building)===snapshot){buildingId.value=null;Object.assign(building,{name:'',type:'宿舍',longitude:null,latitude:null})}}
    else{await saveRepairType(savedId,{...type});if(typeId.value===savedId&&JSON.stringify(type)===snapshot){typeId.value=null;Object.assign(type,{name:'',description:''})}}
    feedback.value='校园资料已保存';await loadCatalog()
  }catch(e){error.value=e instanceof Error?e.message:'保存失败，输入已保留'}finally{busy.value=false}
}
</script>
<template><section class="business-page management-page"><header class="page-heading"><div><p class="eyebrow">校园资料</p><h1>基础资料维护</h1><p>保持人员、楼栋和故障类型准确，派单与地图才有可靠依据。</p></div></header>
  <nav class="management-tabs" aria-label="维护类别"><button v-for="item in tabs" :key="item.key" :aria-current="tab===item.key?'page':undefined" @click="tab=item.key">{{ item.label }}</button></nav>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="feedback" class="notice success" role="status">{{ feedback }}</p>
  <div v-if="tab==='users'" class="management-layout"><section class="management-list"><div class="section-heading"><h2>人员账号</h2><select v-model="role" aria-label="筛选角色"><option value="STUDENT">学生</option><option value="WORKER">维修人员</option><option value="ADMIN">管理员</option></select></div><p v-if="loading" class="field-hint">正在读取账号…</p><p v-else-if="!accounts.length" class="empty-state">暂无账号</p>
      <div v-for="item in accounts" :key="item.id" class="management-row"><div><strong>{{ item.realName }}</strong><small>{{ item.username }} · {{ item.role==='STUDENT'?'学生':item.role==='WORKER'?'维修人员':'管理员' }}<template v-if="item.skillType"> · {{ item.skillType }}</template></small></div><span class="management-state">{{ item.status?'启用':'停用' }}</span><button class="text-button" :disabled="busy" @click="editAccount(item)">编辑人员资料</button><button class="text-button" :disabled="busy" @click="toggleAccount(item)">{{ item.status?'停用账号':'启用账号' }}</button><button v-if="item.role==='WORKER'" class="text-button" :disabled="busy||!item.status" @click="toggleWorker(item)">{{ item.workerStatus?'暂停接单':'恢复接单' }}</button></div>
      <div v-if="total>20" class="management-pages"><button :disabled="page<=1||loading" @click="page--;loadAccounts()">上一页</button><span>{{ page }} / {{ Math.ceil(total/20) }}</span><button :disabled="page*20>=total||loading" @click="page++;loadAccounts()">下一页</button></div>
    </section><form class="management-form" @submit.prevent="submitAccount"><div class="section-heading"><h2>{{ editingId?'编辑人员资料':'创建人员账号' }}</h2><button v-if="editingId" type="button" class="text-button" @click="resetForm">新建</button></div><p v-if="role==='ADMIN'&&!editingId" class="field-hint">管理员账号由现有系统管理；此处仅可维护已有管理员资料和状态。</p><template v-else><label v-if="!editingId">角色<select v-model="form.role"><option value="STUDENT">学生</option><option value="WORKER">维修人员</option></select></label><label v-if="!editingId">用户名<input v-model="form.username" maxlength="64" required /></label><label v-if="!editingId">初始密码<input v-model="form.password" type="password" minlength="8" maxlength="72" required autocomplete="new-password" /></label><label>姓名<input v-model="form.realName" maxlength="64" required /></label><label>电话<input v-model="form.phone" maxlength="20" /></label>
      <template v-if="form.role==='STUDENT'"><label>学号<input v-model="form.studentNo" maxlength="40" required /></label><label>学院<input v-model="form.college" maxlength="80" required /></label><label>班级<input v-model="form.className" maxlength="80" required /></label><label>所在楼栋<select v-model="form.buildingId"><option :value="null">未设置</option><option v-for="item in catalog.buildings" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><label>房间<input v-model="form.roomNo" maxlength="30" /></label></template>
      <template v-if="form.role==='WORKER'"><label>维修技能<input v-model="form.skillType" maxlength="80" required placeholder="如：电工、给排水" /></label><div class="management-coordinates"><label>静态经度<input v-model.number="form.longitude" type="number" step="any" min="-180" max="180" /></label><label>静态纬度<input v-model.number="form.latitude" type="number" step="any" min="-90" max="90" /></label></div><p class="field-hint">坐标是服务驻点，用于静态距离评分；不是实时定位。</p></template><button class="primary-button" type="submit" :disabled="busy">{{ busy?'保存中…':'保存账号' }}</button></template></form></div>
  <div v-else-if="tab==='buildings'" class="management-layout"><section class="management-list"><h2>楼栋与静态坐标</h2><div v-for="item in catalog.buildings" :key="item.id" class="management-row"><div><strong>{{ item.name }}</strong><small>{{ item.type }} · {{ item.longitude===null?'坐标未配置':`${item.longitude}, ${item.latitude}` }}</small></div><button class="text-button" @click="editBuilding(item)">编辑楼栋</button></div></section><form class="management-form" @submit.prevent="submitCatalog('building')"><div class="section-heading"><h2>{{ buildingId?'编辑楼栋':'新增楼栋' }}</h2><button v-if="buildingId" type="button" class="text-button" @click="buildingId=null;Object.assign(building,{name:'',type:'宿舍',longitude:null,latitude:null})">新建</button></div><label>楼栋名称<input v-model="building.name" maxlength="80" required /></label><label>类型<input v-model="building.type" maxlength="30" required /></label><div class="management-coordinates"><label>经度<input v-model.number="building.longitude" type="number" step="any" min="-180" max="180" /></label><label>纬度<input v-model.number="building.latitude" type="number" step="any" min="-90" max="90" /></label></div><p class="field-hint">请同时填写经纬度；演示坐标需明确标识为示例。</p><button class="primary-button" :disabled="busy">保存楼栋</button></form></div>
  <div v-else class="management-layout"><section class="management-list"><h2>故障类型</h2><div v-for="item in catalog.types" :key="item.id" class="management-row"><div><strong>{{ item.name }}</strong><small>{{ item.description }}</small></div><button class="text-button" @click="editType(item)">编辑故障类型</button></div></section><form class="management-form" @submit.prevent="submitCatalog('type')"><div class="section-heading"><h2>{{ typeId?'编辑类型':'新增类型' }}</h2><button v-if="typeId" type="button" class="text-button" @click="typeId=null;Object.assign(type,{name:'',description:''})">新建</button></div><label>名称<input v-model="type.name" maxlength="60" required /></label><label>说明<textarea v-model="type.description" maxlength="300" rows="4" required /></label><button class="primary-button" :disabled="busy">保存类型</button></form></div>
</section></template>
