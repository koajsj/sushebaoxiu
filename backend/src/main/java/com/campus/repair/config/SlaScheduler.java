package com.campus.repair.config;

import com.campus.repair.mapper.RepairOrderMapper;
import com.campus.repair.service.SlaService;
import com.campus.repair.service.OrderWorkflowService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@lombok.RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class SlaScheduler {
    private final RepairOrderMapper orders;
    private final OrderWorkflowService workflow;
    private final SlaService sla;
    @Scheduled(fixedDelayString="${app.sla.scan-delay-ms:60000}",initialDelayString="${app.sla.scan-delay-ms:60000}")
    public void scan() {
        long after=0;
        for(int batch=0;batch<10;batch++) {
            var ids=orders.overdueCandidates(workflow.now(),after);
            if(ids.isEmpty())break;
            for(var id:ids) {
                after=id;
                try{sla.check(id);}catch(RuntimeException failure){log.warn("SLA check rolled back for order {} ({})",id,failure.getClass().getSimpleName());}
            }
        }
    }
}
