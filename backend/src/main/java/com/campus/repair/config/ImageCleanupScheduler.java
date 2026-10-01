package com.campus.repair.config;

import com.campus.repair.service.ImageCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class ImageCleanupScheduler {
    private final ImageCleanupService cleanup;
    @Scheduled(cron="0 30 3 * * *",zone="Asia/Shanghai")
    public void run(){
        String after="";
        for(int batch=0;batch<10;batch++){
            var ids=cleanup.candidates(after);if(ids.isEmpty())return;
            for(var id:ids){after=id;
                try{cleanup.deleteOne(id);}catch(RuntimeException failure){log.warn("Unbound image cleanup deferred id={} reason={}",id,failure.getClass().getSimpleName());}}
        }
    }
}
