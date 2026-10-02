import { http, unwrap } from '../utils/request'
import type { ApiResult,PageResult,UserRole } from '../types'

export interface Account {
  id:number;username:string;realName:string;phone:string|null;role:UserRole;status:number
  studentNo:string|null;college:string|null;className:string|null;buildingId:number|null;roomNo:string|null
  workerId:number|null;skillType:string|null;workerStatus:number|null;longitude:number|null;latitude:number|null
}
export interface AccountForm {
  role:UserRole;username:string;password:string;realName:string;phone:string
  studentNo:string;college:string;className:string;buildingId:number|null;roomNo:string
  skillType:string;longitude:number|null;latitude:number|null
}
export async function getAccounts(role:UserRole,page=1){return unwrap((await http.get<ApiResult<PageResult<Account>>>('/admin/manage/users',{params:{role,page,size:20}})).data)}
export async function createAccount(input:AccountForm){return unwrap((await http.post<ApiResult<number>>('/admin/manage/users',input)).data)}
export async function updateAccount(id:number,input:Omit<AccountForm,'role'|'username'|'password'>){await http.put(`/admin/manage/users/${id}`,input)}
export async function setAccountStatus(id:number,status:number){await http.put(`/admin/manage/users/${id}/status`,{status})}
export async function setWorkerStatus(id:number,status:number){await http.put(`/admin/manage/workers/${id}/status`,{status})}
export interface BuildingForm {name:string;type:string;longitude:number|null;latitude:number|null}
export interface RepairTypeForm {name:string;description:string}
export async function saveBuilding(id:number|null,input:BuildingForm){return unwrap((await (id?http.put<ApiResult<number>>(`/admin/manage/buildings/${id}`,input):http.post<ApiResult<number>>('/admin/manage/buildings',input))).data)}
export async function saveRepairType(id:number|null,input:RepairTypeForm){return unwrap((await (id?http.put<ApiResult<number>>(`/admin/manage/types/${id}`,input):http.post<ApiResult<number>>('/admin/manage/types',input))).data)}
export async function changePassword(oldPassword:string,newPassword:string){await http.put('/users/me/password',{oldPassword,newPassword})}
