package ghIssues;

import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class Issue1464{

    private static final Random RANDOM = new Random();
    private static final Random SECURERANDOM = new SecureRandom();

    // @ExpectWarning("DMI_RANDOM_USED_ONLY_ONCE")
    long m1(){
        return new SecureRandom().nextLong();
    }
    
    // @ExpectWarning("DMI_RANDOM_USED_ONLY_ONCE")
    long m2(){
        return new Random().nextLong();
    }

    long m3() { return RANDOM.nextLong(); }

    long m4() { return SECURERANDOM.nextLong(); }

    long m5() { return ThreadLocalRandom.current().nextLong(); }
}
