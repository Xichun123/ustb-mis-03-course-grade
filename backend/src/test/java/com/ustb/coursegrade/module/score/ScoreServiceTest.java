package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** 业务③核心流程隔离内存库测试（课堂占比、Excel/CSV 导入、外校成绩认定）。 */
class ScoreServiceTest {

    private static final String TERM = "2026-2027-1";

    private JdbcTemplate jdbc;
    private ScoreService service;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);

        for (String ddl : List.of(
                "CREATE TABLE student(student_id VARCHAR(20) PRIMARY KEY,student_name VARCHAR(50),grade INT,degree_level VARCHAR(20),class_name VARCHAR(50),major_id VARCHAR(6),college_id VARCHAR(4))",
                "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),sender_school_name VARCHAR(100))",
                "CREATE TABLE course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),assess_type VARCHAR(2),open_college VARCHAR(10))",
                "CREATE TABLE teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),teacher_id VARCHAR(10),class_ids VARCHAR(100))",
                "CREATE TABLE lesson_class(tc_id VARCHAR(20) PRIMARY KEY,lesson_no VARCHAR(20))",
                "CREATE TABLE sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),study_type INT DEFAULT 1,stu_level VARCHAR(20) DEFAULT '本科',stat_stage INT DEFAULT 1,select_status INT DEFAULT 1)",
                "CREATE TABLE score(score_id BIGINT AUTO_INCREMENT PRIMARY KEY,sc_id BIGINT,exam_type INT DEFAULT 1,exam_flag INT DEFAULT 0,score_type INT DEFAULT 1,usual_ratio DECIMAL(5,2),usual_score DECIMAL(5,2),exam_score DECIMAL(5,2),score_level VARCHAR(10),total_score DECIMAL(5,2),grade_point DECIMAL(5,2),is_final INT DEFAULT 0,score_status INT DEFAULT 0,input_teacher_id VARCHAR(10),submit_time TIMESTAMP,publish_time TIMESTAMP)",
                "CREATE TABLE score_audit(audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,lesson_no VARCHAR(20),submit_time TIMESTAMP,verify_code VARCHAR(6),auditor_id VARCHAR(10),audit_result INT DEFAULT 0,audit_opinion VARCHAR(200),audit_time TIMESTAMP)",
                "CREATE TABLE exchange_score_convert(convert_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),exchange_apply_id VARCHAR(40),term VARCHAR(11),stat_stage INT DEFAULT 1,host_school VARCHAR(150),ext_course_code VARCHAR(30),ext_course_name VARCHAR(100),ext_credit DECIMAL(5,1),ext_score_raw VARCHAR(20),ext_scale_type INT DEFAULT 1,course_id VARCHAR(10),score_type INT DEFAULT 1,converted_score DECIMAL(5,2),converted_level VARCHAR(10),grade_point DECIMAL(5,2),is_final INT DEFAULT 0,proof_no VARCHAR(50),version_no INT DEFAULT 1,audit_status INT DEFAULT 0,apply_time TIMESTAMP,college_auditor_id VARCHAR(10),college_audit_opinion VARCHAR(200),college_audit_time TIMESTAMP,school_auditor_id VARCHAR(10),school_audit_opinion VARCHAR(200),school_audit_time TIMESTAMP)",
                "CREATE TABLE stu_gpa(student_id VARCHAR(20) PRIMARY KEY,earned_credit DECIMAL(6,1),failed_credit DECIMAL(6,1),gpa DECIMAL(4,2),avg_score DECIMAL(5,2),major_rank INT,major_rank_total INT,update_time TIMESTAMP)",
                "CREATE TABLE stu_term_gpa(student_id VARCHAR(20),term VARCHAR(11),term_credit DECIMAL(6,1),term_failed_credit DECIMAL(6,1),update_time TIMESTAMP,PRIMARY KEY(student_id,term))",
                "CREATE TABLE sys_code_dict(dict_id INT AUTO_INCREMENT PRIMARY KEY,dict_group VARCHAR(40),dict_code VARCHAR(40),dict_name VARCHAR(100),is_enabled INT DEFAULT 1)"
        )) {
            jdbc.execute(ddl);
        }

        jdbc.update("INSERT INTO sys_code_dict(dict_group,dict_code,dict_name,is_enabled) VALUES('system_param','usual_ratio_min','30',1)");
        jdbc.update("INSERT INTO student VALUES('S001','张三',2024,'本科','信管241','120102','0001')");
        jdbc.update("INSERT INTO student VALUES('S002','李四',2024,'本科','信管241','120102','0001')");

        jdbc.update("INSERT INTO course VALUES('C11','大学英语','11',2.0,'1','0001')");
        jdbc.update("INSERT INTO course VALUES('C12','管理信息系统','12',3.0,'1','0001')");
        jdbc.update("INSERT INTO course VALUES('C13','数据结构','13',3.5,'1','0001')");
        jdbc.update("INSERT INTO course VALUES('C14','Java企业级开发','14',2.5,'1','0002')");
        jdbc.update("INSERT INTO course VALUES('C15','音乐鉴赏','15',2.0,'1','0001')");
        jdbc.update("INSERT INTO course VALUES('C16','信息系统实训','16',2.0,'2','0001')");

        // 百分制课堂 KT1001（未录入）
        jdbc.update("INSERT INTO teaching_class VALUES('TC1',?,'C12','T001','信管241')", TERM);
        jdbc.update("INSERT INTO lesson_class VALUES('TC1','KT1001')");
        jdbc.update("INSERT INTO sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S001','TC1',1,'本科',1,1)");
        jdbc.update("INSERT INTO sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S002','TC1',1,'本科',1,1)");

        // 五级制考查课课堂 KT1002
        jdbc.update("INSERT INTO teaching_class VALUES('TC2',?,'C16','T001','信管241')", TERM);
        jdbc.update("INSERT INTO lesson_class VALUES('TC2','KT1002')");
        jdbc.update("INSERT INTO sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S001','TC2',1,'本科',1,1)");
        jdbc.update("INSERT INTO sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S002','TC2',1,'本科',1,1)");

        ScoreService target = new ScoreService(jdbc);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        service = (ScoreService) proxy.getProxy();
        asTeacher();
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        jdbc.execute("SHUTDOWN");
    }

    private void asTeacher() {
        UserContext.set(new CurrentUser("t1", "王老师", RoleEnum.TEACHER, null, "E001", "T001", "0001", null));
    }

    private void asCollege() {
        UserContext.set(new CurrentUser("c1", "学院秘书", RoleEnum.COLLEGE_SECRETARY, null, "E002", null, "0001", null));
    }

    private void asOffice() {
        UserContext.set(new CurrentUser("o1", "教务处", RoleEnum.ACADEMIC_OFFICE, null, "E003", null, null, null));
    }

    @Test
    void parseRatioSupportsDecimalFormatAndRejectsInvalidValues() {
        assertEquals(30, ScoreController.parseRatio("30.00", true));
        assertEquals(40, ScoreController.parseRatio(40, true));
        assertNull(ScoreController.parseRatio(null, false));
        assertNull(ScoreController.parseRatio("  ", false));
        assertEquals("INVALID_RATIO", assertThrows(BizException.class, () -> ScoreController.parseRatio("30.5", true)).getCode());
        assertEquals("INVALID_RATIO", assertThrows(BizException.class, () -> ScoreController.parseRatio("abc", true)).getCode());
        assertEquals("INVALID_RATIO", assertThrows(BizException.class, () -> ScoreController.parseRatio("101", true)).getCode());
    }

    @Test
    void setClassRatioHandlesEmptyDraftAndLockedRosters() {
        // 低于下限拦截
        assertEquals("RATIO_TOO_LOW", assertThrows(BizException.class, () -> service.setClassRatio("KT1001", 20)).getCode());

        // 空课堂：不插入垃圾草稿行，返回 pending_new=2 且 persisted=false
        Map<String, Object> emptyRes = service.setClassRatio("KT1001", 40);
        assertEquals(0, emptyRes.get("updated"));
        assertEquals(2, emptyRes.get("pending_new"));
        assertEquals(false, emptyRes.get("persisted"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM score", Integer.class));

        // 录一条草稿后再设置占比：更新该草稿并提示其余 1 名未录入
        Long sc1 = jdbc.queryForObject("SELECT sc_id FROM sc WHERE student_id='S001' AND tc_id='TC1'", Long.class);
        service.saveGrades("KT1001", List.of(Map.of("sc_id", sc1, "usual_score", 85, "exam_score", 90)), 30);
        Map<String, Object> draftRes = service.setClassRatio("KT1001", 35);
        assertEquals(1, draftRes.get("updated"));
        assertEquals(1, draftRes.get("pending_new"));
        assertEquals(true, draftRes.get("persisted"));
        assertEquals(35, jdbc.queryForObject("SELECT usual_ratio FROM score WHERE sc_id=?", Integer.class, sc1));

        // 全部已发布时拦截
        Long sc2 = jdbc.queryForObject("SELECT sc_id FROM sc WHERE student_id='S002' AND tc_id='TC1'", Long.class);
        jdbc.update("UPDATE score SET score_status = 3 WHERE sc_id = ?", sc1);
        jdbc.update("INSERT INTO score(sc_id,exam_type,score_type,usual_ratio,usual_score,exam_score,total_score,grade_point,score_status) VALUES(?,1,1,35,80,80,80,3.4,3)", sc2);
        assertEquals("SCORE_LOCKED", assertThrows(BizException.class, () -> service.setClassRatio("KT1001", 40)).getCode());
    }

    @Test
    void importGradesDryRunDoesNotOvercountLockedRows() {
        Long sc1 = jdbc.queryForObject("SELECT sc_id FROM sc WHERE student_id='S001' AND tc_id='TC1'", Long.class);
        jdbc.update("INSERT INTO score(sc_id,exam_type,score_type,usual_ratio,usual_score,exam_score,total_score,grade_point,score_status) VALUES(?,1,1,30,90,90,90,4.0,3)", sc1);

        // 1 行已发布 + 1 行未录入：dry_run 应返回 preview_count=1, skipped=1, failed=1
        String csvMixed = "学号,平时成绩,期末成绩,考试情况\nS001,88,89,正常\nS002,82,86,正常\n";
        Map<String, Object> previewMixed = service.importGrades(
                "KT1001", new ByteArrayInputStream(csvMixed.getBytes(StandardCharsets.UTF_8)), "mixed.csv", 35, true);
        assertEquals(1, previewMixed.get("preview_count"));
        assertEquals(1, previewMixed.get("skipped"));
        assertEquals(1, previewMixed.get("failed"));

        // 全部为已发布行：dry_run 返回 preview_count=0 并带出详细错误清单；正式导入则抛 NO_VALID_ROW
        String csvLockedOnly = "学号,平时成绩,期末成绩\nS001,88,89\n";
        Map<String, Object> previewLocked = service.importGrades(
                "KT1001", new ByteArrayInputStream(csvLockedOnly.getBytes(StandardCharsets.UTF_8)), "locked.csv", 35, true);
        assertEquals(0, previewLocked.get("preview_count"));
        assertEquals(1, previewLocked.get("failed"));
        assertEquals("NO_VALID_ROW", assertThrows(BizException.class, () -> service.importGrades(
                "KT1001", new ByteArrayInputStream(csvLockedOnly.getBytes(StandardCharsets.UTF_8)), "locked.csv", 35, false)).getCode());
    }

    @Test
    void importGradesSupportsXlsxAndRejectsLegacyXls() throws Exception {
        byte[] xlsx = buildSimpleXlsx(List.of(
                List.of("学号", "平时成绩", "期末成绩", "考试情况"),
                List.of("S001", "85", "92", "正常"),
                List.of("S002", "78", "84", "正常")
        ));
        Map<String, Object> imported = service.importGrades("KT1001", new ByteArrayInputStream(xlsx), "grades.xlsx", 40, false);
        assertEquals(2, imported.get("imported"));
        assertEquals(0, imported.get("failed"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM score WHERE usual_ratio = 40 AND score_type = 1", Integer.class));

        // 拒绝旧版 .xls
        byte[] legacyXlsHeader = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x01};
        BizException ex = assertThrows(BizException.class, () -> service.importGrades(
                "KT1001", new ByteArrayInputStream(legacyXlsHeader), "old.xls", 40, true));
        assertEquals("FILE_PARSE_ERROR", ex.getCode());
        assertTrue(ex.getMessage().contains(".xlsx"));
    }

    @Test
    void importGradesProtectsLevelBasedClassAndSupportsLevelColumn() {
        // 考查课默认返回 default_score_type = 2
        assertEquals(2, service.roster("KT1002").get("default_score_type"));

        // 误传百分制分数到五级制课堂应被拦下，不能覆盖成百分制
        String numericCsv = "学号,平时成绩,期末成绩\nS001,90,88\n";
        Map<String, Object> badPreview = service.importGrades(
                "KT1002", new ByteArrayInputStream(numericCsv.getBytes(StandardCharsets.UTF_8)), "num.csv", 30, true);
        assertEquals(0, badPreview.get("preview_count"));
        assertEquals(1, badPreview.get("failed"));

        // 传入合法等级列应正常导入且保持 score_type = 2
        String levelCsv = "学号,等级成绩,考试情况\nS001,优,正常\nS002,良,正常\n";
        Map<String, Object> ok = service.importGrades(
                "KT1002", new ByteArrayInputStream(levelCsv.getBytes(StandardCharsets.UTF_8)), "lvl.csv", 30, false);
        assertEquals(2, ok.get("imported"));
        assertEquals("优", jdbc.queryForObject(
                "SELECT s.score_level FROM score s JOIN sc ON s.sc_id = sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC2'", String.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT s.score_type FROM score s JOIN sc ON s.sc_id = sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC2'", Integer.class));
    }

    @Test
    void exchangeRequiredCheckCandidatesAndDirectRecognizeAlignWithV13() {
        // 学院秘书也可检索候选课程，且 11/12/13 均识别为必修
        asCollege();
        List<Map<String, Object>> candidates = service.listCourseCandidates("");
        Map<String, Object> c13 = candidates.stream().filter(c -> "C13".equals(c.get("course_id"))).findFirst().orElseThrow();
        assertEquals("专业必修", c13.get("course_type_name"));
        assertEquals(true, c13.get("required"));
        Map<String, Object> c15 = candidates.stream().filter(c -> "C15".equals(c.get("course_id"))).findFirst().orElseThrow();
        assertEquals("通识选修", c15.get("course_type_name"));
        assertEquals(false, c15.get("required"));

        // 按必修认定时：对应 14/15/16 选修/实践被拦；对应 13 专业必修通过
        Map<String, Object> nonRequiredBody = Map.ofEntries(
                Map.entry("student_id", "S001"),
                Map.entry("term", TERM),
                Map.entry("host_school", "测试合作大学"),
                Map.entry("ext_course_name", "Web开发"),
                Map.entry("ext_credit", 2.5),
                Map.entry("ext_score_raw", "A"),
                Map.entry("proof_no", "PF-001"),
                Map.entry("course_id", "C14"),
                Map.entry("score_type", 1),
                Map.entry("converted_score", 90),
                Map.entry("ext_required", true));
        assertEquals("REQUIRED_COURSE_MISMATCH", assertThrows(BizException.class, () -> service.createExchange(nonRequiredBody)).getCode());

        Map<String, Object> requiredBody = Map.ofEntries(
                Map.entry("student_id", "S001"),
                Map.entry("term", TERM),
                Map.entry("host_school", "测试合作大学"),
                Map.entry("ext_course_name", "Data Structures"),
                Map.entry("ext_credit", 3.5),
                Map.entry("ext_score_raw", "92"),
                Map.entry("proof_no", "PF-002"),
                Map.entry("course_id", "C13"),
                Map.entry("score_type", 1),
                Map.entry("converted_score", 92),
                Map.entry("ext_required", true));
        service.createExchange(requiredBody);
        Long convertId = jdbc.queryForObject("SELECT MAX(convert_id) FROM exchange_score_convert", Long.class);

        // 教务处直接认定生效，补齐学院审核字段并更新 is_final 和 stu_gpa
        asOffice();
        Map<String, Object> rec = service.recognizeExchange(convertId, "教务处直接核验生效");
        assertEquals(2, rec.get("audit_status"));
        assertEquals(1, jdbc.queryForObject("SELECT is_final FROM exchange_score_convert WHERE convert_id=?", Integer.class, convertId));
        assertNotNull(jdbc.queryForObject("SELECT college_auditor_id FROM exchange_score_convert WHERE convert_id=?", String.class, convertId));
        assertEquals(0, new java.math.BigDecimal("4.00").compareTo(
                jdbc.queryForObject("SELECT gpa FROM stu_gpa WHERE student_id='S001'", java.math.BigDecimal.class)));
    }

    private static byte[] buildSimpleXlsx(List<List<String>> rows) throws Exception {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            xml.append("<row r=\"").append(r + 1).append("\">");
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                String colRef = String.valueOf((char) ('A' + c)) + (r + 1);
                xml.append("<c r=\"").append(colRef).append("\" t=\"inlineStr\"><is><t>")
                        .append(row.get(c)).append("</t></is></c>");
            }
            xml.append("</row>");
        }
        xml.append("</sheetData></worksheet>");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(xml.toString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
