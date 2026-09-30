package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.repair.common.*;
import com.campus.repair.dto.*;
import com.campus.repair.entity.*;
import com.campus.repair.mapper.*;
import com.campus.repair.vo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@lombok.RequiredArgsConstructor
public class RepairOrderService {
    private final RepairOrderMapper orders;
    private final RepairRecordMapper records;
    private final EvaluationMapper evaluations;
    private final OrderEventMapper events;
    private final BuildingMapper buildings;
    private final RepairTypeMapper types;
    private final StudentMapper students;
    private final WorkerMapper workers;
    private final UserMapper users;
    private final OrderAccessService access;
    private final ImageService images;
    private final NotificationService notifications;
    private final Clock clock;

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")); }
    private RepairOrderEntity locked(long id) {
        var order = orders.lockById(id);
        if (order == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return order;
    }
    private void event(RepairOrderEntity order, UserVO user, String action) {
        var event = new OrderEventEntity();
        event.setOrderId(order.getId()); event.setActorId(user.id()); event.setAction(action);
        event.setStatus(order.getStatus()); event.setCreateTime(now()); events.insert(event);
        notifications.onOrderEvent(order,action);
    }
    private void move(RepairOrderEntity order, UserVO user, OrderStatus expected, OrderStatus target, String action) {
        OrderStatus.require(order.getStatus(), expected);
        order.setStatus(target.name()); order.setUpdateTime(now()); orders.updateById(order); event(order, user, action);
    }

    @Transactional
    public OrderVO create(UserVO user, CreateOrderRequest input) {
        var student = access.student(user);
        if (types.selectById(input.typeId()) == null || buildings.selectById(input.buildingId()) == null)
            throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        var order = new RepairOrderEntity();
        order.setStudentId(student.getId()); order.setTypeId(input.typeId());
        order.setTitle(input.title().strip()); order.setDescription(input.description().strip());
        order.setBuildingId(input.buildingId()); order.setRoomNo(input.roomNo().strip());
        order.setPriority(input.priority()); order.setStatus(OrderStatus.WAIT_AUDIT.name());
        order.setCreateTime(now()); order.setUpdateTime(order.getCreateTime());
        orders.insert(order);
        order.setImageUrl(images.bind(user, input.imageUrl(), order.getId()));
        if (order.getImageUrl() != null) orders.updateById(order);
        event(order, user, "SUBMIT");
        return views(List.of(order)).get(0);
    }

    private LambdaQueryWrapper<RepairOrderEntity> scope(UserVO user) {
        var query = new LambdaQueryWrapper<RepairOrderEntity>();
        switch (user.role()) {
            case STUDENT -> query.eq(RepairOrderEntity::getStudentId, access.student(user).getId());
            case WORKER -> query.eq(RepairOrderEntity::getWorkerId, access.worker(user).getId());
            case ADMIN -> access.requireAdmin(user);
        }
        return query;
    }

    public PageResult<OrderVO> list(UserVO user, OrderQuery input) {
        var query = scope(user);
        var status = OrderStatus.filter(input.getStatus());
        if (status != null) query.eq(RepairOrderEntity::getStatus, status.name());
        if (input.getTypeId() != null) query.eq(RepairOrderEntity::getTypeId, input.getTypeId());
        if (input.getFrom() != null && input.getTo() != null && input.getFrom().isAfter(input.getTo()))
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        if (input.getFrom() != null) query.ge(RepairOrderEntity::getCreateTime, input.getFrom().atStartOfDay());
        if (input.getTo() != null) query.lt(RepairOrderEntity::getCreateTime, input.getTo().plusDays(1).atStartOfDay());
        query.orderByDesc(RepairOrderEntity::getCreateTime, RepairOrderEntity::getId);
        var page = orders.selectPage(new Page<>(input.getPage(), input.getSize()), query);
        return new PageResult<>(views(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** Bulk-load labels per page rather than one lookup per order. */
    private List<OrderVO> views(List<RepairOrderEntity> rows) {
        if (rows.isEmpty()) return List.of();
        var typeNames = types.selectByIds(rows.stream().map(RepairOrderEntity::getTypeId).distinct().toList()).stream()
                .collect(Collectors.toMap(RepairTypeEntity::getId, RepairTypeEntity::getName));
        var buildingNames = buildings.selectByIds(rows.stream().map(RepairOrderEntity::getBuildingId).distinct().toList()).stream()
                .collect(Collectors.toMap(BuildingEntity::getId, BuildingEntity::getName));
        var studentRows = students.selectByIds(rows.stream().map(RepairOrderEntity::getStudentId).distinct().toList());
        var workerIds = rows.stream().map(RepairOrderEntity::getWorkerId).filter(Objects::nonNull).distinct().toList();
        var workerRows = workerIds.isEmpty() ? List.<WorkerEntity>of() : workers.selectByIds(workerIds);
        var userIds = new HashSet<Long>();
        studentRows.forEach(row -> userIds.add(row.getUserId())); workerRows.forEach(row -> userIds.add(row.getUserId()));
        var names = users.selectByIds(userIds).stream().collect(Collectors.toMap(UserEntity::getId, UserEntity::getRealName));
        var studentNames = studentRows.stream().collect(Collectors.toMap(StudentEntity::getId, row -> names.get(row.getUserId())));
        var workerNames = workerRows.stream().collect(Collectors.toMap(WorkerEntity::getId, row -> names.get(row.getUserId())));
        return rows.stream().map(row -> OrderVO.from(row, typeNames.get(row.getTypeId()), buildingNames.get(row.getBuildingId()),
                workerNames.get(row.getWorkerId()), studentNames.get(row.getStudentId()))).toList();
    }

    @Transactional(readOnly=true)
    public OrderDetailVO detail(UserVO user, long id) {
        var order = orders.selectById(id); access.requireView(user, order);
        return new OrderDetailVO(views(List.of(order)).get(0),
                records.selectList(new LambdaQueryWrapper<RepairRecordEntity>().eq(RepairRecordEntity::getOrderId, id).orderByAsc(RepairRecordEntity::getId)),
                evaluations.selectOne(new LambdaQueryWrapper<EvaluationEntity>().eq(EvaluationEntity::getOrderId, id)),
                events.selectList(new LambdaQueryWrapper<OrderEventEntity>().eq(OrderEventEntity::getOrderId, id).orderByAsc(OrderEventEntity::getId)));
    }

    @Transactional
    public void audit(UserVO user, long id) {
        access.requireAdmin(user); var order = locked(id);
        move(order, user, OrderStatus.WAIT_AUDIT, OrderStatus.WAIT_ASSIGN, "AUDIT");
    }

    @Transactional
    public void assign(UserVO user, long id, long workerId) {
        access.requireAdmin(user); var order = locked(id);
        OrderStatus.require(order.getStatus(), OrderStatus.WAIT_ASSIGN);
        if (order.getWorkerId() != null) throw new BusinessException(ErrorCode.CONFLICT);
        var worker = workers.lockById(workerId); access.requireAvailable(worker);
        order.setWorkerId(workerId); order.setUpdateTime(now()); orders.updateById(order); event(order,user,"ASSIGN");
    }

    @Transactional
    public void accept(UserVO user, long id) {
        var order = locked(id); access.requireWorkerOwner(user, order);
        move(order, user, OrderStatus.WAIT_ASSIGN, OrderStatus.ASSIGNED, "ACCEPT");
    }
    @Transactional
    public void start(UserVO user, long id) {
        var order = locked(id); access.requireWorkerOwner(user, order);
        move(order, user, OrderStatus.ASSIGNED, OrderStatus.PROCESSING, "START");
    }
    @Transactional
    public void record(UserVO user, RepairRecordRequest input) {
        var order = locked(input.orderId()); var worker = access.requireWorkerOwner(user, order);
        OrderStatus.require(order.getStatus(), OrderStatus.PROCESSING);
        var started = events.selectOne(new LambdaQueryWrapper<OrderEventEntity>().eq(OrderEventEntity::getOrderId,order.getId())
                .eq(OrderEventEntity::getAction,"START"));
        var record = new RepairRecordEntity(); record.setOrderId(order.getId()); record.setWorkerId(worker.getId());
        record.setContent(input.content().strip()); record.setStartTime(started.getCreateTime());
        record.setImageUrl(images.bind(user, input.imageUrl(), order.getId())); records.insert(record);
    }
    @Transactional
    public void finish(UserVO user, long id) {
        var order = locked(id); var worker = access.requireWorkerOwner(user, order);
        OrderStatus.require(order.getStatus(), OrderStatus.PROCESSING);
        if (records.selectCount(new LambdaQueryWrapper<RepairRecordEntity>().eq(RepairRecordEntity::getOrderId,id)) == 0)
            throw new BusinessException(ErrorCode.RECORD_REQUIRED);
        records.update(null, new LambdaUpdateWrapper<RepairRecordEntity>().eq(RepairRecordEntity::getOrderId,id)
                .set(RepairRecordEntity::getFinishTime,now()));
        workers.incrementTasks(worker.getId());
        move(order,user,OrderStatus.PROCESSING,OrderStatus.WAIT_CONFIRM,"FINISH");
    }
    @Transactional
    public void confirm(UserVO user, long id) {
        var order = locked(id); access.requireStudentOwner(user, order);
        move(order,user,OrderStatus.WAIT_CONFIRM,OrderStatus.FINISHED,"CONFIRM");
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void evaluate(UserVO user, EvaluationRequest input) {
        var order = locked(input.orderId()); access.requireStudentOwner(user, order);
        OrderStatus.require(order.getStatus(),OrderStatus.FINISHED);
        // Serialize evaluations for one worker so a concurrent AVG cannot lose another rating.
        var worker = workers.lockById(order.getWorkerId());
        var evaluation = new EvaluationEntity(); evaluation.setOrderId(order.getId()); evaluation.setStudentId(order.getStudentId());
        evaluation.setScore(input.score()); evaluation.setContent(input.content()==null?"":input.content().strip());
        evaluation.setCreateTime(now()); evaluations.insert(evaluation);
        var ratings = evaluations.selectList(new LambdaQueryWrapper<EvaluationEntity>().in(EvaluationEntity::getOrderId,
                orders.selectList(new LambdaQueryWrapper<RepairOrderEntity>().eq(RepairOrderEntity::getWorkerId,worker.getId()))
                        .stream().map(RepairOrderEntity::getId).toList()));
        var sum = ratings.stream().mapToInt(EvaluationEntity::getScore).sum();
        worker.setScore(BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(ratings.size()),2,RoundingMode.HALF_UP));
        workers.update(null, new LambdaUpdateWrapper<WorkerEntity>().eq(WorkerEntity::getId,worker.getId())
                .set(WorkerEntity::getScore,worker.getScore()));
        move(order,user,OrderStatus.FINISHED,OrderStatus.COMMENTED,"EVALUATE");
    }

    public Map<String, Object> catalog() {
        return Map.of("types",types.selectList(new LambdaQueryWrapper<RepairTypeEntity>().orderByAsc(RepairTypeEntity::getId)),
                "buildings",buildings.selectList(new LambdaQueryWrapper<BuildingEntity>().orderByAsc(BuildingEntity::getId)));
    }
    public List<Map<String,Object>> availableWorkers(UserVO user) {
        access.requireAdmin(user);
        return workers.selectList(new LambdaQueryWrapper<WorkerEntity>().eq(WorkerEntity::getStatus,1).orderByAsc(WorkerEntity::getId))
                .stream().filter(worker -> { var account=users.selectById(worker.getUserId()); return account!=null && account.getStatus()==1 && account.getRole()==com.campus.repair.security.UserRole.WORKER; })
                .map(worker -> { var account=users.selectById(worker.getUserId()); return Map.<String,Object>of("id",worker.getId(),"name",account.getRealName(),
                        "username",account.getUsername(),"skillType",worker.getSkillType(),"score",worker.getScore(),"taskCount",worker.getTaskCount()); }).toList();
    }
    public Map<String,Long> summary(UserVO user) {
        var result=new LinkedHashMap<String,Long>();
        result.put("total",orders.selectCount(scope(user)));
        result.put("pending",orders.selectCount(scope(user).in(RepairOrderEntity::getStatus,"WAIT_AUDIT","WAIT_ASSIGN","ASSIGNED")));
        result.put("active",orders.selectCount(scope(user).in(RepairOrderEntity::getStatus,"PROCESSING","WAIT_CONFIRM")));
        result.put("completed",orders.selectCount(scope(user).in(RepairOrderEntity::getStatus,"FINISHED","COMMENTED")));
        var today=now().toLocalDate().atStartOfDay();
        result.put("today",user.role()==com.campus.repair.security.UserRole.WORKER
                ? orders.countAssignedToday(access.worker(user).getId(),today)
                : orders.selectCount(scope(user).ge(RepairOrderEntity::getCreateTime,today)));
        return result;
    }
}
