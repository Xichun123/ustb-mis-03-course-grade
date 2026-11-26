package com.ustb.coursegrade.module.stats;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 业务④ 学业完成情况（分类学分统计、毕业达标预判、门禁与权限隔离、本研兼容）隔离内存库测试。 */
class StatsServiceTest {

    private static final String TERM = "2026-2027-1";

    private JdbcTemplate jdbc;
    private StatsService service;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);

        for (String ddl : List.of(
                "CREATE TABLE sys_dict_item(dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(40),item_code VARCHAR(100),item_name VARCHAR(200),item_status VARCHAR(2) DEFAULT '01')",
                "CREATE TABLE org_organization(org_id VARCHAR(6) PRIMARY KEY,org_name VARCHAR(100),college_id VARCHAR(4))",
                "CREATE TABLE sys_person(person_id VARCHAR(20) PRIMARY KEY,person_name VARCHAR(100))",
                "CREATE TABLE hr_employee(emp_id VARCHAR(10) PRIMARY KEY,emp_name VARCHAR(50))",
                "CREATE TABLE aca_class(class_id VARCHAR(10) PRIMARY KEY,class_name VARCHAR(50))",
                "CREATE TABLE aca_major(major_id VARCHAR(6) PRIMARY KEY,major_name VARCHAR(100),org_id VARCHAR(6),college_id VARCHAR(4),duration INT DEFAULT 4,degree_name VARCHAR(20),min_credit DECIMAL(5,1))",
                "CREATE TABLE std_student(student_id VARCHAR(20) PRIMARY KEY,person_id VARCHAR(20),class_id VARCHAR(10),major_id VARCHAR(6),org_id VARCHAR(6),grade VARCHAR(40),student_type VARCHAR(2),student_status VARCHAR(2))",
                "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),sender_school_name VARCHAR(100),term_ref VARCHAR(11),college_ref VARCHAR(4),major_ref VARCHAR(6),start_on DATE,end_on DATE,status_code VARCHAR(20))",
                "CREATE TABLE aca_course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),org_id VARCHAR(6))",
                "CREATE TABLE aca_program_course(major_id VARCHAR(6),grade VARCHAR(40),program_type VARCHAR(20),course_id VARCHAR(10),course_type VARCHAR(2),credit DECIMAL(5,1),open_semester INT,PRIMARY KEY(major_id,grade,program_type,course_id))",
                "CREATE TABLE grad_scheme_assignment(assignment_id VARCHAR(50) PRIMARY KEY,student_id VARCHAR(20),scheme_id VARCHAR(50),direction_id VARCHAR(20),valid_from DATE,valid_to DATE)",
                "CREATE TABLE grad_scheme_course(scheme_course_id VARCHAR(50) PRIMARY KEY,scheme_id VARCHAR(50),course_id VARCHAR(10),direction_id VARCHAR(20),course_category INT,suggest_term INT)",
                "CREATE TABLE aca_teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),emp_id VARCHAR(10),tc_status VARCHAR(2) DEFAULT '4')",
                "CREATE TABLE sel_lesson_class(tc_id VARCHAR(20) PRIMARY KEY,lesson_no VARCHAR(20))",
                "CREATE TABLE sel_sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),study_type INT DEFAULT 1,stat_stage INT DEFAULT 1,select_status INT DEFAULT 1)",
                "CREATE TABLE sel_score(score_id BIGINT AUTO_INCREMENT PRIMARY KEY,sc_id BIGINT,exam_type INT DEFAULT 1,score_type INT DEFAULT 1,total_score DECIMAL(5,2),score_level VARCHAR(10),grade_point DECIMAL(5,2),is_final INT DEFAULT 1,score_status INT DEFAULT 3,published_at TIMESTAMP)",
                "CREATE TABLE sel_exchange_score_convert(convert_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),term VARCHAR(11),stat_stage INT DEFAULT 1,course_id VARCHAR(10),score_type INT DEFAULT 1,converted_score DECIMAL(5,2),converted_level VARCHAR(10),grade_point DECIMAL(5,2),audit_status INT DEFAULT 2,school_audit_time TIMESTAMP)",
                "CREATE TABLE sel_teach_eval(sc_id BIGINT PRIMARY KEY,eval_score DECIMAL(5,2),eval_comment VARCHAR(200),eval_time TIMESTAMP)",
                "CREATE TABLE sel_stu_gpa(student_id VARCHAR(20) PRIMARY KEY,earned_credit DECIMAL(5,1),failed_credit DECIMAL(5,1),gpa DECIMAL(5,2),avg_score DECIMAL(5,2),major_rank INT,major_rank_total INT,updated_at TIMESTAMP)"
        )) {
            jdbc.execute(ddl);
        }

        jdbc.update("INSERT INTO sys_dict_item(dict_type,item_code,item_name,item_status) VALUES('system_param','current_term',?,'01')", TERM);
        jdbc.update("INSERT INTO org_organization VALUES('ORG001','经济管理学院','0001'),('ORG002','计通学院','0002')");
        jdbc.update("INSERT INTO hr_employee VALUES('E001','王老师')");
        jdbc.update("INSERT INTO aca_class VALUES('CLS241','信管241'),('CLS26M','管科硕261')");
        jdbc.update("INSERT INTO aca_major VALUES('120102','信息管理与信息系统','ORG001','0001',4,'管理学学士',10.0),('120100','管理科学与工程','ORG001','0001',3,'管理学硕士',5.0)");

        jdbc.update("INSERT INTO sys_person VALUES('P1','张三'),('P2','李四'),('PM1','王硕')");
        jdbc.update("INSERT INTO std_student VALUES('U001','P1','CLS241','120102','ORG001','2024','01','01')");
        jdbc.update("INSERT INTO std_student VALUES('U002','P2','CLS241','120102','ORG002','2024','01','01')");
        jdbc.update("INSERT INTO std_student VALUES('M001','PM1','CLS26M','120100','ORG001','2026','02','01')");

        // 覆盖全部 6 类课程性质
        jdbc.update("INSERT INTO aca_course VALUES('C11','大学英语','11',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C12','管理信息系统','12',3.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C13','数据库原理','13',3.5,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C14','数据可视化','14',2.5,'ORG002')");
        jdbc.update("INSERT INTO aca_course VALUES('C15','音乐鉴赏','15',1.5,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C16','开发实践','16',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('CMIN','辅修金融学','14',3.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('G01','管理研究方法','12',3.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('G02','大数据分析方向课','13',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('G03','供应链其他方向课','13',2.0,'ORG001')");

        // 本科培养方案（必修：C11 2.0 + C12 3.0 + C13 3.5；实践：C16 2.0）
        jdbc.update("INSERT INTO aca_program_course VALUES('120102','2024','1','C11','11',2.0,1)");
        jdbc.update("INSERT INTO aca_program_course VALUES('120102','2024','1','C12','12',3.0,3)");
        jdbc.update("INSERT INTO aca_program_course VALUES('120102','2024','1','C13','13',3.5,4)");
        jdbc.update("INSERT INTO aca_program_course VALUES('120102','2024','1','C16','16',2.0,5)");

        // 研究生方案（M001 属 DIR01，包含 G01 年度必修 + G02 方向必修，不含 G03）
        jdbc.update("INSERT INTO grad_scheme_assignment VALUES('ASN1','M001','SCH_M','DIR01','2026-01-01',NULL)");
        jdbc.update("INSERT INTO grad_scheme_course VALUES('SC1','SCH_M','G01',NULL,1,1)");
        jdbc.update("INSERT INTO grad_scheme_course VALUES('SC2','SCH_M','G02','DIR01',2,1)");
        jdbc.update("INSERT INTO grad_scheme_course VALUES('SC3','SCH_M','G03','DIR02',2,1)");

        service = new StatsService(jdbc);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        jdbc.execute("SHUTDOWN");
    }

    private void asStudent(String sid) {
        UserContext.set(new CurrentUser(sid, sid, RoleEnum.STUDENT, sid, null, null, "0001", null));
    }

    private void asOffice() {
        UserContext.set(new CurrentUser("off", "教务处", RoleEnum.ACADEMIC_OFFICE, null, "E900", null, null, null));
    }

    private void asCollege(String collegeId) {
        UserContext.set(new CurrentUser("col", "学院秘书", RoleEnum.COLLEGE_SECRETARY, null, "E800", null, collegeId, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void academicProgressCountsPassedOnlyDeduplicatesRetakesExcludesMinorAndChecksGraduation() {
        // 教学班：C11（往期）、C12（首次不及格 + 本期重修及格）、C13（不及格）、C15（在修待出分）、C16（停开不计在修）、CMIN（辅修及格）
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC11','2025-2026-2','C11','E001','4')");
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC12_OLD','2025-2026-2','C12','E001','4')");
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC12_NEW',?,'C12','E001','4')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC13',?,'C13','E001','4')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC15',?,'C15','E001','4')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TC16_STOP',?,'C16','E001','2')", TERM);
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TCMIN','2025-2026-2','CMIN','E001','4')");

        // 选课记录
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(1,'U001','TC11',1,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(2,'U001','TC12_OLD',1,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(3,'U001','TC12_NEW',2,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(4,'U001','TC13',1,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(5,'U001','TC15',1,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(6,'U001','TC16_STOP',1,1,1)");
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(7,'U001','TCMIN',3,1,1)");

        // 成绩：C11 85及格(2.0)；C12 首次45不及格 + 重修80及格(只计一次3.0)；C13 52不及格(0获学分,3.5不及格)；辅修CMIN 90及格(不计主修)；外校认定C14 88及格(2.5)
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(1,1,85.0,3.70,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(2,1,45.0,0.00,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(3,1,80.0,3.00,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(4,1,52.0,0.00,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(7,1,90.0,4.00,3,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO sel_exchange_score_convert(student_id,term,stat_stage,course_id,score_type,converted_score,grade_point,audit_status,school_audit_time) VALUES('U001','2025-2026-2',1,'C14',1,88.0,3.70,2,CURRENT_TIMESTAMP)");

        // 学生未完成当前学期评教时（sc_id=3,4,5 均正常开课未评教，停开的 sc_id=6 不触发评教），应被 EVAL_REQUIRED 拦截
        asStudent("U001");
        BizException gateEx = assertThrows(BizException.class, () -> service.getAcademicProgress(null));
        assertEquals("EVAL_REQUIRED", gateEx.getCode());

        // 完成 3 门正常开课课程评教（无需评已停开的 TC16_STOP）
        jdbc.update("INSERT INTO sel_teach_eval VALUES(3,95,NULL,CURRENT_TIMESTAMP),(4,90,NULL,CURRENT_TIMESTAMP),(5,92,NULL,CURRENT_TIMESTAMP)");

        Map<String, Object> progress = service.getAcademicProgress(null);
        Map<String, Object> grad = (Map<String, Object>) progress.get("graduation_check");
        // 已获主修学分 = C11(2.0) + C12(3.0, 去重) + 外校C14(2.5) = 7.5（不含不及格C13和辅修CMIN）
        assertEquals("7.5", grad.get("earned_credit"));
        assertEquals("3.5", grad.get("failed_credit"));
        // 在修学分 = C15(1.5)（已停开TC16_STOP不计入在修！）
        assertEquals("1.5", grad.get("in_progress_credit"));
        assertEquals("10.0", grad.get("graduation_min_credit"));
        assertEquals("2.5", grad.get("total_shortfall_credit"));
        assertEquals("NOT_ELIGIBLE", grad.get("status"));
        assertEquals(false, grad.get("credit_satisfied"));
        assertEquals(List.of("数据库原理"), grad.get("unpassed_required_courses"));

        // 辅修单独统计，不混入主修
        Map<String, Object> minor = (Map<String, Object>) progress.get("minor_summary");
        assertEquals("3.0", minor.get("earned_credit"));
        assertEquals(1, minor.get("passed_count"));

        // 核对六类课程分类明细
        List<Map<String, Object>> cats = (List<Map<String, Object>>) progress.get("categories");
        assertEquals(6, cats.size());
        Map<String, Object> c12Cat = cats.stream().filter(c -> "12".equals(c.get("course_type"))).findFirst().orElseThrow();
        assertEquals("3.0", c12Cat.get("earned_credit"));
        assertEquals("0.0", c12Cat.get("failed_credit"));
        Map<String, Object> c13Cat = cats.stream().filter(c -> "13".equals(c.get("course_type"))).findFirst().orElseThrow();
        assertEquals("0.0", c13Cat.get("earned_credit"));
        assertEquals("3.5", c13Cat.get("failed_credit"));
        Map<String, Object> c14Cat = cats.stream().filter(c -> "14".equals(c.get("course_type"))).findFirst().orElseThrow();
        assertEquals("2.5", c14Cat.get("earned_credit"));

        // 重修通过 C13(3.5) 后：总已获 11.0 >= 10.0 且全部 3 门必修课通过 -> ELIGIBLE 可申请毕业
        jdbc.update("UPDATE sel_score SET total_score=78.0, grade_point=3.00 WHERE sc_id=4");
        Map<String, Object> passedAll = service.getAcademicProgress(null);
        Map<String, Object> gradOk = (Map<String, Object>) passedAll.get("graduation_check");
        assertEquals("11.0", gradOk.get("earned_credit"));
        assertEquals("0.0", gradOk.get("total_shortfall_credit"));
        assertEquals("ELIGIBLE", gradOk.get("status"));
        assertEquals(true, gradOk.get("credit_satisfied"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void graduateSchemeProgressAndPermissionIsolationAreEnforced() {
        // 权限隔离：学生不能查他人，学院秘书不能跨学院查
        asStudent("U001");
        assertThrows(BizException.class, () -> service.getAcademicProgress("U002"));
        asCollege("0001");
        assertThrows(BizException.class, () -> service.getAcademicProgress("U002"));

        // 研究生 M001（DIR01）：仅对 G01(3.0) 与 G02(2.0) 考核，不包含 DIR02 的 G03
        jdbc.update("INSERT INTO aca_teaching_class VALUES('TCG1',?,'G01','E001','4')", TERM);
        jdbc.update("INSERT INTO sel_sc(sc_id,student_id,tc_id,study_type,stat_stage,select_status) VALUES(10,'M001','TCG1',1,2,1)");
        jdbc.update("INSERT INTO sel_score(sc_id,score_type,total_score,grade_point,score_status,published_at) VALUES(10,1,88.0,3.70,3,CURRENT_TIMESTAMP)");

        asOffice();
        Map<String, Object> gradProg = service.getAcademicProgress("M001");
        List<Map<String, Object>> planCourses = (List<Map<String, Object>>) gradProg.get("plan_courses");
        assertEquals(List.of("G01", "G02"), planCourses.stream().map(r -> r.get("course_id")).toList());

        Map<String, Object> gradCheck = (Map<String, Object>) gradProg.get("graduation_check");
        assertEquals("3.0", gradCheck.get("earned_credit"));
        assertEquals("5.0", gradCheck.get("graduation_min_credit"));
        assertEquals("2.0", gradCheck.get("total_shortfall_credit"));
        assertEquals("NOT_ELIGIBLE", gradCheck.get("status"));
        assertEquals(List.of("大数据分析方向课"), gradCheck.get("unpassed_required_courses"));
    }
}
