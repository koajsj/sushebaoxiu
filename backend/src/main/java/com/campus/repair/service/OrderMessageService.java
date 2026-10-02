package com.campus.repair.service;

import com.campus.repair.utils.BusinessTime;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.*;
import com.campus.repair.dto.MessageRequest;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class OrderMessageService {
    private final ChatMessageMapper messages;
    private final RepairOrderMapper orders;
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final UserMapper users;
    private final OrderAccessService access;
    private final Clock clock;
    public record Context(long orderId,String title,Long workerId,String workerName) {}
    private LocalDateTime now(){return BusinessTime.now(clock);}
    private RepairOrderEntity allowed(UserVO user,long orderId,boolean lock){
        var order=lock?orders.lockById(orderId):orders.selectById(orderId);
        access.requireView(user,order);return order;
    }
    @Transactional(readOnly=true)
    public Context context(UserVO user,long orderId){
        var order=allowed(user,orderId,false);
        String name=null;
        if(order.getWorkerId()!=null){var worker=workers.selectById(order.getWorkerId());
            if(worker!=null){var account=users.selectById(worker.getUserId());if(account!=null)name=account.getRealName();}}
        return new Context(orderId,order.getTitle(),order.getWorkerId(),name);
    }
    private MessageVO view(ChatMessageEntity row,Map<Long,String> names){
        return new MessageVO(row.getId(),row.getOrderId(),row.getSenderId(),names.getOrDefault(row.getSenderId(),"用户"),
                row.getReceiverId(),row.getContent(),row.getReadStatus()==1,row.getCreateTime());
    }
    @Transactional
    public List<MessageVO> list(UserVO user,long orderId,Long beforeId,Long afterId,int size){
        if(beforeId!=null&&afterId!=null)throw new BusinessException(ErrorCode.BAD_REQUEST);
        allowed(user,orderId,false);
        var query=new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getOrderId,orderId);
        if(user.role()==UserRole.WORKER)query.and(q->q.eq(ChatMessageEntity::getSenderId,user.id()).or().eq(ChatMessageEntity::getReceiverId,user.id()));
        if(beforeId!=null)query.lt(ChatMessageEntity::getId,beforeId);
        if(afterId!=null)query.gt(ChatMessageEntity::getId,afterId);
        var page=messages.selectPage(new Page<>(1,size,false),afterId!=null?
                query.orderByAsc(ChatMessageEntity::getId):query.orderByDesc(ChatMessageEntity::getId));
        var rows=new ArrayList<>(page.getRecords());if(afterId==null)Collections.reverse(rows);
        var received=rows.stream().filter(row->row.getReceiverId().equals(user.id())&&row.getReadStatus()==0)
                .map(ChatMessageEntity::getId).toList();
        if(user.role()!=UserRole.ADMIN&&!received.isEmpty()){
            messages.update(null,new LambdaUpdateWrapper<ChatMessageEntity>().in(ChatMessageEntity::getId,received)
                    .eq(ChatMessageEntity::getReceiverId,user.id()).eq(ChatMessageEntity::getReadStatus,0)
                    .set(ChatMessageEntity::getReadStatus,1));
            rows.stream().filter(row->row.getReceiverId().equals(user.id())).forEach(row->row.setReadStatus(1));
        }
        var ids=rows.stream().map(ChatMessageEntity::getSenderId).distinct().toList();
        Map<Long,String> names=new HashMap<>();
        if(!ids.isEmpty())users.selectByIds(ids).forEach(account->names.put(account.getId(),account.getRealName()));
        return rows.stream().map(row->view(row,names)).toList();
    }
    @Transactional
    public MessageVO send(UserVO user,long orderId,MessageRequest input){
        var order=allowed(user,orderId,true);
        if(user.role()==UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        if(input.expectedWorkerId()!=null&&!input.expectedWorkerId().equals(order.getWorkerId())) throw new BusinessException(ErrorCode.CONFLICT);
        if(order.getWorkerId()==null) throw new BusinessException(ErrorCode.CONFLICT);
        long recipient=user.role()==UserRole.STUDENT?workers.selectById(order.getWorkerId()).getUserId()
                :students.selectById(order.getStudentId()).getUserId();
        var row=new ChatMessageEntity();row.setOrderId(orderId);row.setSenderId(user.id());row.setReceiverId(recipient);
        row.setContent(input.content().strip());row.setReadStatus(0);row.setCreateTime(now());messages.insert(row);
        return view(row,Map.of(user.id(),user.realName()));
    }
}
