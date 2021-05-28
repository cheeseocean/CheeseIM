package beer.cheese.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.netty.util.internal.SystemPropertyUtil;

public class SystemUtilsTest {

    @Test
    public void testIsMac(){
        Assertions.assertTrue(SystemUtils.isMac(), "Not Mac");
    }
}
