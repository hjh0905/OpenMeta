package info.openmeta.framework.orm.meta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Load the global system cache, using the highest priority Runner.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppStartupRunner implements CommandLineRunner {

    @Autowired
    ModelManager modelManager;

    @Autowired
    OptionManager optionManager;

    @Override
    public void run(String... args) throws Exception {
        // 1. init model manager
        modelManager.init();
        // 2. init option manager
        optionManager.init();
    }

}
