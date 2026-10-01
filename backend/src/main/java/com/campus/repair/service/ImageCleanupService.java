package com.campus.repair.service;

import com.campus.repair.mapper.RepairImageMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@lombok.extern.slf4j.Slf4j
public class ImageCleanupService {
    private final RepairImageMapper images;
    private final Clock clock;
    private final Path directory;
    public ImageCleanupService(RepairImageMapper images,Clock clock,@Value("${app.upload.directory:./uploads}") String directory){
        this.images=images;this.clock=clock;this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    public List<String> candidates(String after){
        var cutoff=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai")).minusDays(1);
        return images.oldUnbound(cutoff,after);
    }
    @Transactional
    public void deleteOne(String id){
        var row=images.lockById(id);
        var cutoff=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Shanghai")).minusDays(1);
        if(row==null||row.getOrderId()!=null||!row.getCreateTime().isBefore(cutoff)||images.referenceCount(id)!=0)return;
        images.deleteById(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try {Files.deleteIfExists(directory.resolve(id));}
                catch(IOException failure){log.warn("Unbound image file cleanup failed id={} reason={}",id,failure.getClass().getSimpleName());}
            }
        });
    }
}
