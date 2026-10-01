package com.campus.repair.security;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Small bounded local guard; remote address comes from the socket, not an untrusted header. */
@Component
public class LoginAttemptGuard {
    private record Attempt(int failures,Instant expiresAt) {}
    private final Map<String,Attempt> attempts=new LinkedHashMap<>();
    private final Clock clock;
    public LoginAttemptGuard(Clock clock){this.clock=clock;}
    private String key(String username,String address){return address+":"+username;}
    public synchronized void check(String username,String address){
        var attempt=attempts.get(key(username,address));
        if(attempt!=null&&attempt.expiresAt().isAfter(clock.instant())&&attempt.failures()>=5)
            throw new BusinessException(ErrorCode.RATE_LIMITED);
    }
    public synchronized void failed(String username,String address){
        String key=key(username,address);Instant now=clock.instant();var previous=attempts.get(key);
        int failures=previous!=null&&previous.expiresAt().isAfter(now)?previous.failures()+1:1;
        attempts.put(key,new Attempt(failures,now.plusSeconds(300)));
        if(attempts.size()>10000){var iterator=attempts.keySet().iterator();iterator.next();iterator.remove();}
    }
    public synchronized void success(String username,String address){attempts.remove(key(username,address));}
}
