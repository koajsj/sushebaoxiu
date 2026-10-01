package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.RepairImageEntity;
import com.campus.repair.entity.RepairOrderEntity;
import com.campus.repair.mapper.RepairImageMapper;
import com.campus.repair.mapper.RepairOrderMapper;
import com.campus.repair.mapper.UserMapper;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.UserVO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageServiceTest {
    @TempDir Path directory;

    @Test void replacedWorkerCannotReadEvenTheirOwnBoundUpload() throws Exception {
        var images = mock(RepairImageMapper.class);
        var orders = mock(RepairOrderMapper.class);
        var access = mock(OrderAccessService.class);
        var uploader = new UserVO(2,"worker001","维修员",null,UserRole.WORKER);
        var order = new RepairOrderEntity(); order.setId(11L); order.setWorkerId(3L);
        var image = image(11L);
        Files.write(directory.resolve(image.getId()),new byte[]{1,2,3});
        when(images.selectById(image.getId())).thenReturn(image);
        when(orders.selectById(11L)).thenReturn(order);
        doThrow(new BusinessException(ErrorCode.NOT_FOUND)).when(access).requireView(uploader,order);
        var service = new ImageService(images,orders,mock(UserMapper.class),access,Clock.systemUTC(),directory.toString());

        var failure = assertThrows(BusinessException.class,()->service.read(uploader,image.getId()));
        assertEquals(ErrorCode.NOT_FOUND,failure.getErrorCode());
    }

    @Test void ownerCanPreviewAnUnboundUpload() throws Exception {
        var images = mock(RepairImageMapper.class);
        var orders = mock(RepairOrderMapper.class);
        var access = mock(OrderAccessService.class);
        var image = image(null);
        Files.write(directory.resolve(image.getId()),new byte[]{1,2,3});
        when(images.selectById(image.getId())).thenReturn(image);
        var service = new ImageService(images,orders,mock(UserMapper.class),access,Clock.systemUTC(),directory.toString());

        assertArrayEquals(new byte[]{1,2,3},service.read(
                new UserVO(2,"worker001","维修员",null,UserRole.WORKER),image.getId()).bytes());
        verifyNoInteractions(orders,access);
    }

    private static RepairImageEntity image(Long orderId) {
        var image = new RepairImageEntity();
        image.setId("11111111-1111-4111-8111-111111111111");
        image.setOwnerId(2L); image.setOrderId(orderId); image.setContentType("image/png");
        return image;
    }
}
