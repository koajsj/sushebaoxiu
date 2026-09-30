package com.campus.repair.service;

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
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai")).withNano(0);}
    private RepairOrderEntity allowed(UserVO user,long orderId){
        var order=orders.lockById(orderId);access.requireView(user,order);return order;
    }
    private MessageVO view(ChatMessageEntity row,Map<Long,String> names){
        return new MessageVO(row.getId(),row.getOrderId(),row.getSenderId(),names.getOrDefault(row.getSenderId(),"用户"),
                row.getReceiverId(),row.getContent(),row.getReadStatus()==1,row.getCreateTime());
    }
    @Transactional
    public List<MessageVO> list(UserVO user,long orderId){
        allowed(user,orderId);
        var query=new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getOrderId,orderId);
        if(user.role()==UserRole.WORKER)query.and(q->q.eq(ChatMessageEntity::getSenderId,user.id()).or().eq(ChatMessageEntity::getReceiverId,user.id()));
        var page=messages.selectPage(new Page<>(1,100,false),query.orderByDesc(ChatMessageEntity::getId));
        var rows=new ArrayList<>(page.getRecords());Collections.reverse(rows);
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
        var order=allowed(user,orderId);
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
