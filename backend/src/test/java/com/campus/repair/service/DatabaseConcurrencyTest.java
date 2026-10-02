package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.dto.*;
import com.campus.repair.entity.UserEntity;
import com.campus.repair.mapper.UserMapper;
import com.campus.repair.vo.UserVO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in, real InnoDB locking regressions. Never run against an existing business/demo DB. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="CHECK_DB_NAME", matches="campus_repair_deep_check_[a-z0-9_]+")
@EnabledIfEnvironmentVariable(named="DB_NAME", matches="campus_repair_deep_check_[a-z0-9_]+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseConcurrencyTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired RepairOrderService orders;
    @Autowired OrderWorkflowService workflow;
    @Autowired AdminManagementService management;
    @Autowired ImageService images;
    @Autowired NotificationService notifications;
    @Autowired Executor notificationExecutor;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void isolatedDatabaseOnly() {
        String database=jdbc.queryForObject("SELECT DATABASE()",String.class);
        assertEquals(System.getenv("CHECK_DB_NAME"),database);
        assertNotNull(database);
        assertTrue(database.matches("campus_repair_deep_check_[a-z0-9_]+"));
    }
    private UserVO user(String name) {
        return UserVO.from(users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername,name)));
    }
    private long workerId(UserVO worker) {
        return jdbc.queryForObject("SELECT id FROM worker WHERE user_id=?",Long.class,worker.id());
    }
    private Connection hold(String table,long id) throws Exception {
        Connection connection=dataSource.getConnection();connection.setAutoCommit(false);
        try(var statement=connection.prepareStatement("SELECT id FROM `"+table+"` WHERE id=? FOR UPDATE")) {
            statement.setLong(1,id);try(var rows=statement.executeQuery()){assertTrue(rows.next());}
        }
        return connection;
    }
    private void waitForLocks(String table,int count) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do {
            int waiting=jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w "
                    +"JOIN performance_schema.data_locks l ON l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID "
                    +"AND l.ENGINE=w.ENGINE WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME=?",Integer.class,table);
            if(waiting>=count)return;
            Thread.sleep(10);
        }while(System.nanoTime()<deadline);
        fail("Concurrent requests did not reach the held "+table+" lock");
    }
    private long assigned(UserVO student,UserVO admin,UserVO worker) {
        long type=jdbc.queryForObject("SELECT MIN(id) FROM repair_type",Long.class);
        long building=jdbc.queryForObject("SELECT MIN(id) FROM building",Long.class);
        long id=orders.create(student,new CreateOrderRequest(type,"并发回归验证","仅隔离库测试",null,building,"101","NORMAL",UUID.randomUUID().toString())).id();
        orders.audit(admin,id);orders.assign(admin,id,workerId(worker));orders.accept(worker,id);return id;
    }
    private ErrorCode outcome(Runnable action) {
        try{action.run();return null;}catch(BusinessException error){return error.getErrorCode();}
    }

    @Test @Order(1) void overlappingConfirmationsCannotBothCommit() throws Exception {
        var student=user("student001");var admin=user("admin001");var worker=user("worker001");
        long first=assigned(student,admin,worker),second=assigned(student,admin,worker);
        var start=LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        workflow.propose(worker,first,new AppointmentRequest(start,start.plusHours(1),jdbc.queryForObject("SELECT appointment_version FROM repair_order WHERE id=?",Integer.class,first)));
        workflow.propose(worker,second,new AppointmentRequest(start,start.plusHours(1),jdbc.queryForObject("SELECT appointment_version FROM repair_order WHERE id=?",Integer.class,second)));
        int firstVersion=jdbc.queryForObject("SELECT appointment_version FROM repair_order WHERE id=?",Integer.class,first);
        int secondVersion=jdbc.queryForObject("SELECT appointment_version FROM repair_order WHERE id=?",Integer.class,second);
        var executor=Executors.newFixedThreadPool(2);
        try(var held=hold("worker",workerId(worker))) {
            var a=executor.submit(()->outcome(()->workflow.respond(student,first,new AppointmentResponse(firstVersion,true,null))));
            var b=executor.submit(()->outcome(()->workflow.respond(student,second,new AppointmentResponse(secondVersion,true,null))));
            waitForLocks("worker",2);held.commit();
            var one=a.get(10,TimeUnit.SECONDS);var two=b.get(10,TimeUnit.SECONDS);
            assertTrue((one==null&&two==ErrorCode.APPOINTMENT_CONFLICT)||(two==null&&one==ErrorCode.APPOINTMENT_CONFLICT),
                    "Exactly one overlapping appointment must commit; outcomes="+one+", "+two);
        }finally{executor.shutdownNow();}
    }

    @Test @Order(2) void concurrentUploadsCannotExceedUnboundQuota() throws Exception {
        var student=user("student001");
        var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        var file=new MockMultipartFile("file","test.png","image/png",output.toByteArray());
        for(int i=0;i<9;i++)images.upload(student,file);
        var executor=Executors.newFixedThreadPool(2);
        try(var held=hold("user",student.id())) {
            var a=executor.submit(()->outcome(()->images.upload(student,file)));
            var b=executor.submit(()->outcome(()->images.upload(student,file)));
            waitForLocks("user",2);held.commit();
            var one=a.get(10,TimeUnit.SECONDS);var two=b.get(10,TimeUnit.SECONDS);
            assertTrue((one==null&&two==ErrorCode.UPLOAD_LIMIT)||(two==null&&one==ErrorCode.UPLOAD_LIMIT),
                    "Only one final upload slot may be consumed; outcomes="+one+", "+two);
            assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM repair_image WHERE owner_id=? AND order_id IS NULL",Integer.class,student.id()));
        }finally{executor.shutdownNow();}
    }

    @Test @Order(3) void profileEditPreservesConcurrentWorkerBusinessFields() throws Exception {
        var admin=user("admin001");var worker=user("worker002");long id=workerId(worker);
        var executor=Executors.newSingleThreadExecutor();
        try(var held=hold("worker",id)) {
            var saved=executor.submit(()->management.update(admin,worker.id(),new AccountUpdateRequest(
                    "资料更新",null,null,null,null,null,null,"电工",null,null)));
            waitForLocks("worker",1);
            try(var statement=held.prepareStatement("UPDATE worker SET status=0, score=4.75, task_count=7 WHERE id=?")) {
                statement.setLong(1,id);assertEquals(1,statement.executeUpdate());
            }
            held.commit();saved.get(10,TimeUnit.SECONDS);
            assertEquals(0,jdbc.queryForObject("SELECT status FROM worker WHERE id=?",Integer.class,id));
            assertEquals(0,new BigDecimal("4.75").compareTo(jdbc.queryForObject("SELECT score FROM worker WHERE id=?",BigDecimal.class,id)));
            assertEquals(7,jdbc.queryForObject("SELECT task_count FROM worker WHERE id=?",Integer.class,id));
            assertEquals("电工",jdbc.queryForObject("SELECT skill_type FROM worker WHERE id=?",String.class,id));
        }finally{executor.shutdownNow();}
    }

    @Test @Order(4) void rollbackDoesNotNotifyAndConcurrentDeliveryIsIdempotent() throws Exception {
        var student=user("student001");
        long type=jdbc.queryForObject("SELECT MIN(id) FROM repair_type",Long.class);
        long building=jdbc.queryForObject("SELECT MIN(id) FROM building",Long.class);
        var input=new CreateOrderRequest(type,"事务回滚验证","仅隔离库测试",null,building,"101","NORMAL",UUID.randomUUID().toString());
        var rolledBack=new AtomicLong();
        new TransactionTemplate(transactions).execute(status->{
            rolledBack.set(orders.create(student,input).id());status.setRollbackOnly();return null;
        });
        var pool=((ThreadPoolTaskExecutor)notificationExecutor).getThreadPoolExecutor();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(pool.getActiveCount()>0||!pool.getQueue().isEmpty()) {
            assertTrue(System.nanoTime()<deadline,"Notification executor did not drain");Thread.sleep(10);
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM repair_order WHERE id=?",Integer.class,rolledBack.get()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE idempotency_key LIKE ?",Integer.class,"SUBMIT:"+rolledBack.get()+":%"));
        String key="deep-concurrent:"+UUID.randomUUID();
        var event=new BusinessNotificationEvent(student.id(),"SUBMIT",rolledBack.get(),123,"幂等验证","仅隔离库测试",key);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->notifications.write(event));var b=executor.submit(()->notifications.write(event));
            a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE idempotency_key=?",Integer.class,key));
        }finally{executor.shutdownNow();}
    }
}
