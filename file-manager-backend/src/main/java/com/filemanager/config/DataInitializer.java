package com.filemanager.config;

import com.filemanager.entity.User;
import com.filemanager.enums.Role;
import com.filemanager.enums.UserStatus;
import com.filemanager.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setEmail("admin@example.com");
            admin.setNickname("系统管理员");
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            userRepository.save(admin);
            
            System.out.println("======================================");
            System.out.println("默认管理员账号已创建:");
            System.out.println("用户名: admin");
            System.out.println("密码: admin123");
            System.out.println("请登录后立即修改密码！");
            System.out.println("======================================");
        }
    }
}