package com.sra.journal_tracking.test;

import java.time.LocalDate;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.ResearchField;
import com.sra.journal_tracking.entity.jpa.ResearchPaper;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.ResearchFieldRepository;
import com.sra.journal_tracking.repository.jpa.ResearchPaperRepository;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;

@Component
@Profile("dev")
public class DatabaseTestRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final ResearchFieldRepository fieldRepository;
    private final ResearchPaperRepository paperRepository;

    public DatabaseTestRunner(UserRepository userRepository, 
                              RoleRepository roleRepository,
                              ApiSourceRepository apiSourceRepository,
                              ResearchFieldRepository fieldRepository,
                              ResearchPaperRepository paperRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.apiSourceRepository = apiSourceRepository;
        this.fieldRepository = fieldRepository;
        this.paperRepository = paperRepository;
    }

    @Override
    @Transactional // Đảm bảo nếu lỗi thì rollback toàn bộ, không tạo dữ liệu rác
    public void run(String... args) throws Exception {
        System.out.println("===========================================");
        System.out.println(" BẮT ĐẦU TEST LƯU DỮ LIỆU VÀO SQL SERVER ");

        try {
            // Dùng thời gian hiện tại để tạo các chuỗi Unique, tránh lỗi Duplicate Key ở các lần chạy sau
            String uniqueSuffix = String.valueOf(System.currentTimeMillis()).substring(6);

            // ---------------------------------------------------------
            // 1. TẠO DỮ LIỆU ADMIN & USER (Nhóm 1)
            // ---------------------------------------------------------
            Role userRole = new Role();
            userRole.setRoleName("RESEARCHER_" + uniqueSuffix); 
            userRole.setDescription("Vai trò test hệ thống");
            roleRepository.save(userRole);

            User testUser = new User();
            testUser.setEmail("long.research." + uniqueSuffix + "@example.com");
            testUser.setPasswordHash("hashed_password_123");
            testUser.setFullName("Long");
            testUser.setRole(userRole);
            userRepository.save(testUser);
            System.out.println("✅ Đã lưu thành công User & Role!");

            // ---------------------------------------------------------
            // 2. TẠO DỮ LIỆU HỌC THUẬT NỀN TẢNG (Nhóm 2)
            // ---------------------------------------------------------
            
            // A. Phải có Nguồn API (ApiSource) trước vì ResearchPaper bắt buộc cần
            ApiSource source = new ApiSource();
            source.setSourceName("Semantic_Scholar_" + uniqueSuffix);
            source.setBaseUrl("https://api.semanticscholar.org");
            source.setRateLimitRpm(100);
            apiSourceRepository.save(source);

            // B. Phải có Lĩnh vực nghiên cứu (ResearchField) 
            ResearchField field = new ResearchField();
            field.setFieldName("Computer Science_" + uniqueSuffix);
            fieldRepository.save(field);

            // C. Cuối cùng mới tạo Bài báo (ResearchPaper) kết nối các khóa ngoại lại
            ResearchPaper paper = new ResearchPaper();
            paper.setTitle("Xu hướng Graph Database " + uniqueSuffix);
            paper.setAbstractText("Nghiên cứu về hệ thống Journal-Trend-Tracking...");
            paper.setDoi("10.1234/test." + uniqueSuffix);
            paper.setPubDate(LocalDate.now());
            paper.setPubYear((short) 2026);
            // Gắn Khóa ngoại (Foreign Keys)
            paper.setSource(source); 
            paper.setField(field);

            paperRepository.save(paper);
            
            System.out.println("✅ Đã lưu thành công Bài báo kết nối với API Source và Field!");
            System.out.println("🎉 TOÀN BỘ DỮ LIỆU ĐÃ THÔNG SUỐT XUỐNG DATABASE 🎉");

        } catch (Exception e) {
            System.out.println("❌ CÓ LỖI XẢY RA TRONG QUÁ TRÌNH TEST:");
            e.printStackTrace();
        }
        
        System.out.println("===========================================");
    }
}
