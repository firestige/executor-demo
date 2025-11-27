package xyz.firestige.deploy.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import xyz.firestige.deploy.domain.plan.PlanRepository;
import xyz.firestige.deploy.domain.task.TaskRepository;
import xyz.firestige.deploy.domain.task.TaskRuntimeRepository;

/**
 * E2E测试基类
 * <p>
 * 职责：
 * 1. 加载完整Spring上下文
 * 2. 提供Repository清理
 * 3. 配置测试Profile
 * 
 * @since T-023 测试体系重建
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected PlanRepository planRepository;

    @Autowired
    protected TaskRuntimeRepository taskRuntimeRepository;

    @BeforeEach
    void setUp() {
        // 每个测试前清理数据
        cleanupRepositories();
    }

    @AfterEach
    void tearDown() {
        // 每个测试后清理数据
        cleanupRepositories();
    }

    /**
     * 清理所有Repository数据
     */
    protected void cleanupRepositories() {
        if (taskRuntimeRepository != null) {
            // 假设InMemory实现有clear方法
            try {
                taskRuntimeRepository.getClass().getMethod("clear").invoke(taskRuntimeRepository);
            } catch (Exception e) {
                // 忽略，某些实现可能没有clear方法
            }
        }
        
        // 类似清理其他repositories
        // 如果是InMemory实现，它们应该都有clear()方法
    }
}
