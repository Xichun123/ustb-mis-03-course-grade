package com.ustb.coursegrade.module.score;

import com.ustb.coursegrade.common.BizException;
import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import com.ustb.coursegrade.module.correction.CorrectionService;
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
    private CorrectionService correctionService;

    @BeforeEach
    void setup() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);

        for (String ddl : List.of(
                "CREATE TABLE org_organization(org_id VARCHAR(6) PRIMARY KEY,org_name VARCHAR(100),college_id VARCHAR(4))",
                "CREATE TABLE sys_person(person_id VARCHAR(20) PRIMARY KEY,person_name VARCHAR(100))",
                "CREATE TABLE hr_employee(emp_id VARCHAR(10) PRIMARY KEY,emp_name VARCHAR(50))",
                "CREATE TABLE aca_class(class_id VARCHAR(10) PRIMARY KEY,class_name VARCHAR(50))",
                "CREATE TABLE std_student(student_id VARCHAR(20) PRIMARY KEY,person_id VARCHAR(20),class_id VARCHAR(10),major_id VARCHAR(6),org_id VARCHAR(6),grade VARCHAR(40),student_type VARCHAR(2),student_status VARCHAR(2))",
                "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),sender_school_name VARCHAR(100))",
                "CREATE TABLE aca_course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),org_id VARCHAR(6))",
                "CREATE TABLE aca_course_id(course_id VARCHAR(10) PRIMARY KEY,assess_type VARCHAR(20),open_college VARCHAR(20))",
                "CREATE TABLE aca_teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),emp_id VARCHAR(10),class_ids VARCHAR(100),tc_status VARCHAR(2) DEFAULT '4',hours DECIMAL(18,2) DEFAULT 48.00,no_open_reason VARCHAR(100))",
                "CREATE TABLE sel_lesson_class(tc_id VARCHAR(20) PRIMARY KEY,lesson_no VARCHAR(20))",
                "CREATE TABLE sel_sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),study_type INT DEFAULT 1,stu_level VARCHAR(20) DEFAULT '本科',stat_stage INT DEFAULT 1,select_status INT DEFAULT 1)",
                "CREATE TABLE sel_score(score_id BIGINT AUTO_INCREMENT PRIMARY KEY,sc_id BIGINT,exam_type INT DEFAULT 1,exam_flag INT DEFAULT 0,score_type INT DEFAULT 1,usual_ratio DECIMAL(5,2),usual_score DECIMAL(5,2),exam_score DECIMAL(5,2),score_level VARCHAR(10),total_score DECIMAL(5,2),grade_point DECIMAL(5,2),is_final INT DEFAULT 0,score_status INT DEFAULT 0,input_teacher_id VARCHAR(10),submitted_at TIMESTAMP,published_at TIMESTAMP)",
                "CREATE TABLE sel_score_audit(audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,lesson_no VARCHAR(20),submitted_at TIMESTAMP,verify_code VARCHAR(6),auditor_id VARCHAR(10),audit_result INT DEFAULT 0,audit_opinion VARCHAR(500),reviewed_at TIMESTAMP)",
                "CREATE TABLE sel_score_correction(apply_id BIGINT AUTO_INCREMENT PRIMARY KEY,score_id BIGINT,apply_teacher_id VARCHAR(10),old_score DECIMAL(5,2),new_score DECIMAL(5,2),old_level VARCHAR(20),new_level VARCHAR(20),apply_reason VARCHAR(500),submitted_at TIMESTAMP,apply_status INT DEFAULT 0,college_auditor_id VARCHAR(10),college_audit_opinion VARCHAR(200),college_audit_time TIMESTAMP,auditor_id VARCHAR(10),audit_opinion VARCHAR(500),reviewed_at TIMESTAMP)",
                "CREATE TABLE sel_exchange_score_convert(convert_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),exchange_apply_id VARCHAR(50),term VARCHAR(11),stat_stage INT DEFAULT 1,host_school VARCHAR(200),ext_course_code VARCHAR(30),ext_course_name VARCHAR(100),ext_credit DECIMAL(5,1),ext_score_raw VARCHAR(20),ext_scale_type INT DEFAULT 1,course_id VARCHAR(10),score_type INT DEFAULT 1,converted_score DECIMAL(5,2),converted_level VARCHAR(20),grade_point DECIMAL(5,2),is_final INT DEFAULT 0,proof_no VARCHAR(50),version_no INT DEFAULT 1,audit_status INT DEFAULT 0,submitted_at TIMESTAMP,college_auditor_id VARCHAR(10),college_audit_opinion VARCHAR(200),college_audit_time TIMESTAMP,school_auditor_id VARCHAR(10),school_audit_opinion VARCHAR(200),school_audit_time TIMESTAMP)",
                "CREATE TABLE sel_stu_gpa(student_id VARCHAR(20) PRIMARY KEY,earned_credit DECIMAL(5,1),failed_credit DECIMAL(5,1),gpa DECIMAL(5,2),avg_score DECIMAL(5,2),major_rank INT,major_rank_total INT,updated_at TIMESTAMP)",
                "CREATE TABLE sel_stu_term_gpa(student_id VARCHAR(20),term VARCHAR(11),term_credit DECIMAL(5,1),term_failed_credit DECIMAL(5,1),updated_at TIMESTAMP,PRIMARY KEY(student_id,term))",
                "CREATE TABLE sys_dict_item(dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(40),item_code VARCHAR(100),item_name VARCHAR(200),item_status VARCHAR(2) DEFAULT '01')"
        )) {
            jdbc.execute(ddl);
        }

        jdbc.update("INSERT INTO hr_employee VALUES('E001','王老师'),('E002','学院秘书'),('E003','教务处')");

        jdbc.update("INSERT INTO sys_dict_item(dict_type,item_code,item_name,item_status) VALUES('system_param','usual_ratio_min','30','01')");
        jdbc.update("INSERT INTO org_organization VALUES('ORG001','经济管理学院','0001')");
        jdbc.update("INSERT INTO org_organization VALUES('ORG002','计通学院','0002')");
        jdbc.update("INSERT INTO aca_class VALUES('CLS241','信管241')");
        jdbc.update("INSERT INTO sys_person VALUES('P_S001','张三'),('P_S002','李四')");
        jdbc.update("INSERT INTO std_student VALUES('S001','P_S001','CLS241','120102','ORG001','2024','01','01')");
        jdbc.update("INSERT INTO std_student VALUES('S002','P_S002','CLS241','120102','ORG001','2024','01','01')");

        jdbc.update("INSERT INTO aca_course VALUES('C11','大学英语','11',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C12','管理信息系统','12',3.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C13','数据结构','13',3.5,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C14','Java企业级开发','14',2.5,'ORG002')");
        jdbc.update("INSERT INTO aca_course VALUES('C15','音乐鉴赏','15',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course VALUES('C16','信息系统实训','16',2.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course_id VALUES('C11','1','0001'),('C12','1','0001'),('C13','1','0001'),('C14','1','0002'),('C15','1','0001'),('C16','2','0001')");

        // 百分制课堂 KT1001（未录入）
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_ids,tc_status) VALUES('TC1',?,'C12','E001','信管241','4')", TERM);
        jdbc.update("INSERT INTO sel_lesson_class VALUES('TC1','KT1001')");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S001','TC1',1,'本科',1,1)");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S002','TC1',1,'本科',1,1)");

        // 五级制考查课课堂 KT1002
        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_ids,tc_status) VALUES('TC2',?,'C16','E001','信管241','4')", TERM);
        jdbc.update("INSERT INTO sel_lesson_class VALUES('TC2','KT1002')");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S001','TC2',1,'本科',1,1)");
        jdbc.update("INSERT INTO sel_sc(student_id,tc_id,study_type,stu_level,stat_stage,select_status) VALUES('S002','TC2',1,'本科',1,1)");

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        TransactionInterceptor txInterceptor = new TransactionInterceptor(txManager, new AnnotationTransactionAttributeSource());

        ProxyFactory proxy = new ProxyFactory(new ScoreService(jdbc));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(txInterceptor);
        service = (ScoreService) proxy.getProxy();

        ProxyFactory corrProxy = new ProxyFactory(new CorrectionService(jdbc));
        corrProxy.setProxyTargetClass(true);
        corrProxy.addAdvice(txInterceptor);
        correctionService = (CorrectionService) corrProxy.getProxy();
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

    private void asStudent(String studentId) {
        UserContext.set(new CurrentUser("stu_" + studentId, "学生", RoleEnum.STUDENT, studentId, null, null, "0001", "120102"));
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
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sel_score", Integer.class));

        // 录一条草稿后再设置占比：更新该草稿并提示其余 1 名未录入
        Long sc1 = jdbc.queryForObject("SELECT sc_id FROM sel_sc WHERE student_id='S001' AND tc_id='TC1'", Long.class);
        service.saveGrades("KT1001", List.of(Map.of("sc_id", sc1, "usual_score", 85, "exam_score", 90)), 30);
        Map<String, Object> draftRes = service.setClassRatio("KT1001", 35);
        assertEquals(1, draftRes.get("updated"));
        assertEquals(1, draftRes.get("pending_new"));
        assertEquals(true, draftRes.get("persisted"));
        assertEquals(35, jdbc.queryForObject("SELECT usual_ratio FROM sel_score WHERE sc_id=?", Integer.class, sc1));

        // 全部已发布时拦截
        Long sc2 = jdbc.queryForObject("SELECT sc_id FROM sel_sc WHERE student_id='S002' AND tc_id='TC1'", Long.class);
        jdbc.update("UPDATE sel_score SET score_status = 3 WHERE sc_id = ?", sc1);
        jdbc.update("INSERT INTO sel_score(sc_id,exam_type,score_type,usual_ratio,usual_score,exam_score,total_score,grade_point,score_status) VALUES(?,1,1,35,80,80,80,3.4,3)", sc2);
        assertEquals("SCORE_LOCKED", assertThrows(BizException.class, () -> service.setClassRatio("KT1001", 40)).getCode());
    }

    @Test
    void importGradesDryRunDoesNotOvercountLockedRows() {
        Long sc1 = jdbc.queryForObject("SELECT sc_id FROM sel_sc WHERE student_id='S001' AND tc_id='TC1'", Long.class);
        jdbc.update("INSERT INTO sel_score(sc_id,exam_type,score_type,usual_ratio,usual_score,exam_score,total_score,grade_point,score_status) VALUES(?,1,1,30,90,90,90,4.0,3)", sc1);

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
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sel_score WHERE usual_ratio = 40 AND score_type = 1", Integer.class));

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
                "SELECT s.score_level FROM sel_score s JOIN sel_sc sc ON s.sc_id = sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC2'", String.class));
        assertEquals(2, jdbc.queryForObject(
                "SELECT s.score_type FROM sel_score s JOIN sel_sc sc ON s.sc_id = sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC2'", Integer.class));
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
        Long convertId = jdbc.queryForObject("SELECT MAX(convert_id) FROM sel_exchange_score_convert", Long.class);

        // 教务处直接认定生效，补齐学院审核字段并更新 is_final 和 sel_stu_gpa
        asOffice();
        Map<String, Object> rec = service.recognizeExchange(convertId, "教务处直接核验生效");
        assertEquals(2, rec.get("audit_status"));
        assertEquals(1, jdbc.queryForObject("SELECT is_final FROM sel_exchange_score_convert WHERE convert_id=?", Integer.class, convertId));
        assertNotNull(jdbc.queryForObject("SELECT college_auditor_id FROM sel_exchange_score_convert WHERE convert_id=?", String.class, convertId));
        assertEquals(0, new java.math.BigDecimal("4.00").compareTo(
                jdbc.queryForObject("SELECT gpa FROM sel_stu_gpa WHERE student_id='S001'", java.math.BigDecimal.class)));
    }

    @Test
    void excelTemplateExportRoundTripAndHeaderDisambiguation() throws Exception {
        // 1. 导出百分制课堂模板：标准 .xlsx ZIP，不含冗余“说明：”提示，且可被解析器直接回读
        byte[] tpl = service.exportTemplate("KT1001");
        assertTrue(tpl.length > 100 && tpl[0] == 'P' && tpl[1] == 'K');
        String sheetXml = readSheetXml(tpl);
        assertFalse(sheetXml.contains("说明："));
        assertTrue(sheetXml.contains("KT1001"));
        assertTrue(sheetXml.contains("管理信息系统"));
        List<Map<String, String>> parsedTpl = ScoreExcelParser.parse(new ByteArrayInputStream(tpl), "grades_KT1001.xlsx");
        assertEquals(2, parsedTpl.size());
        assertEquals("S001", parsedTpl.get(0).get("student_id"));
        assertEquals("S002", parsedTpl.get(1).get("student_id"));
        assertEquals("正常", parsedTpl.get(0).get("exam_flag"));

        // 2. 导出五级制课堂模板同样合法可读
        byte[] levelTpl = service.exportTemplate("KT1002");
        assertTrue(levelTpl.length > 100 && levelTpl[0] == 'P' && levelTpl[1] == 'K');
        List<Map<String, String>> parsedLevelTpl = ScoreExcelParser.parse(new ByteArrayInputStream(levelTpl), "grades_KT1002.xlsx");
        assertEquals(2, parsedLevelTpl.size());

        // 3. 表头兼容“平时分”“考试成绩”，且“期末占比”列不会被误识别为期末考试成绩；预览与正式导入均返回明细 rows
        byte[] customXlsx = buildSimpleXlsx(List.of(
                List.of("课堂号", "KT1001", "课程名称", "管理信息系统", "学期", TERM),
                List.of("学号", "姓名", "平时分", "期末占比", "考试成绩", "考试情况"),
                List.of("S001", "张三", "80", "70", "90", "正常"),
                List.of("S002", "李四", "86", "70", "94", "正常")
        ));
        Map<String, Object> preview = service.importGrades("KT1001", new ByteArrayInputStream(customXlsx), "custom.xlsx", 35, true);
        assertEquals(2, preview.get("preview_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> previewRows = (List<Map<String, Object>>) preview.get("rows");
        assertNotNull(previewRows);
        assertEquals(2, previewRows.size());
        assertEquals("张三", previewRows.get(0).get("student_name"));
        assertEquals(0, new java.math.BigDecimal("86.5").compareTo(new java.math.BigDecimal(String.valueOf(previewRows.get(0).get("total_score")))));

        Map<String, Object> imported = service.importGrades("KT1001", new ByteArrayInputStream(customXlsx), "custom.xlsx", 35, false);
        assertEquals(2, imported.get("imported"));
        assertEquals(0, imported.get("failed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> importedRows = (List<Map<String, Object>>) imported.get("rows");
        assertEquals(2, importedRows.size());
        assertEquals(0, new java.math.BigDecimal("90.00").compareTo(jdbc.queryForObject(
                "SELECT s.exam_score FROM sel_score s JOIN sel_sc sc ON s.sc_id=sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC1'",
                java.math.BigDecimal.class)));
        assertEquals(0, new java.math.BigDecimal("80.00").compareTo(jdbc.queryForObject(
                "SELECT s.usual_score FROM sel_score s JOIN sel_sc sc ON s.sc_id=sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC1'",
                java.math.BigDecimal.class)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sel_score WHERE usual_ratio = 35", Integer.class));
    }

    @Test
    void gradeCorrectionClosedLoopRequiresUnlockResubmitPaperVerifyAndRepublish() throws Exception {
        // 阶段一：正常按课堂统一占比 30% 导入并完成首次提交 -> 学院核对 -> 教务处发布
        byte[] initXlsx = buildSimpleXlsx(List.of(
                List.of("学号", "平时成绩", "考试成绩", "考试情况"),
                List.of("S001", "70", "80", "正常"),
                List.of("S002", "90", "90", "正常")
        ));
        service.importGrades("KT1001", new ByteArrayInputStream(initXlsx), "init.xlsx", 30, false);
        Map<String, Object> firstSubmit = service.submit("KT1001");
        String code1 = String.valueOf(firstSubmit.get("verify_code"));
        assertEquals(2, firstSubmit.get("submitted"));
        assertEquals(false, firstSubmit.get("is_correction_resubmit"));

        asCollege();
        service.audit("KT1001", code1, 1, "首次纸质成绩单核对无误");
        asOffice();
        Map<String, Object> firstPub = service.publish("KT1001");
        assertEquals(2, firstPub.get("published"));
        assertEquals(0, firstPub.get("finalized_corrections"));

        Long s1ScoreId = jdbc.queryForObject(
                "SELECT s.score_id FROM sel_score s JOIN sel_sc sc ON s.sc_id=sc.sc_id WHERE sc.student_id='S001' AND sc.tc_id='TC1'",
                Long.class);
        Long s2ScId = jdbc.queryForObject("SELECT sc_id FROM sel_sc WHERE student_id='S002' AND tc_id='TC1'", Long.class);
        assertEquals(0, new java.math.BigDecimal("2.40").compareTo(
                jdbc.queryForObject("SELECT gpa FROM sel_stu_gpa WHERE student_id='S001'", java.math.BigDecimal.class)));

        // 阶段二：教师发起成绩更正，学院批准后仅解锁 S001（apply_status=1, score_status=4），S002 仍锁定
        asTeacher();
        Map<String, Object> applyRes = correctionService.submitCorrection(Map.of(
                "score_id", s1ScoreId,
                "expected_score", 77.0,
                "new_score", 91.5,
                "apply_reason", "漏登平时作业加分"
        ));
        long applyId = Long.parseLong(String.valueOf(applyRes.get("apply_id")));
        assertEquals(0, applyRes.get("apply_status"));

        asOffice();
        assertEquals("FORBIDDEN", assertThrows(BizException.class,
                () -> correctionService.officeDecision(applyId, 1, "direct")).getCode());

        asCollege();
        Map<String, Object> approveRes = correctionService.collegeDecision(applyId, 1, "同意解锁重录并补交纸质单");
        assertEquals(1, approveRes.get("apply_status"));
        assertEquals(4, approveRes.get("score_status"));
        assertEquals("KT1001", approveRes.get("lesson_no"));
        assertEquals(4, jdbc.queryForObject("SELECT score_status FROM sel_score WHERE score_id=?", Integer.class, s1ScoreId));
        assertEquals(0, jdbc.queryForObject("SELECT is_final FROM sel_score WHERE score_id=?", Integer.class, s1ScoreId));
        assertNull(jdbc.queryForObject("SELECT published_at FROM sel_score WHERE score_id=?", Object.class, s1ScoreId));
        assertEquals(3, jdbc.queryForObject("SELECT score_status FROM sel_score WHERE sc_id=?", Integer.class, s2ScId));

        // 阶段三：教师端看到更正待重录提示；S002 受锁定保护；导出模板仅含待重录的 S001；Excel 导入新成绩并重提生成新核对码
        asTeacher();
        List<Map<String, Object>> myClasses = service.myClasses(TERM, "KT1001");
        assertEquals(1, ((Number) myClasses.get(0).get("correction_pending_count")).intValue());
        Map<String, Object> roster = service.roster("KT1001");
        assertEquals(true, roster.get("has_active_correction"));
        assertEquals("SCORE_LOCKED", assertThrows(BizException.class,
                () -> service.saveGrades("KT1001", List.of(Map.of("sc_id", s2ScId, "usual_score", 60, "exam_score", 60)), 30)).getCode());

        byte[] corrTpl = service.exportTemplate("KT1001");
        List<Map<String, String>> corrTplRows = ScoreExcelParser.parse(new ByteArrayInputStream(corrTpl), "grades_KT1001.xlsx");
        assertEquals(1, corrTplRows.size());
        assertEquals("S001", corrTplRows.get(0).get("student_id"));

        byte[] corrImport = buildSimpleXlsx(List.of(
                List.of("学号", "平时成绩", "考试成绩", "考试情况"),
                List.of("S001", "95", "90", "正常")
        ));
        Map<String, Object> reImp = service.importGrades("KT1001", new ByteArrayInputStream(corrImport), "corr.xlsx", 30, false);
        assertEquals(1, reImp.get("imported"));

        Map<String, Object> secondSubmit = service.submit("KT1001");
        String code2 = String.valueOf(secondSubmit.get("verify_code"));
        assertEquals(1, secondSubmit.get("submitted"));
        assertEquals(true, secondSubmit.get("is_correction_resubmit"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sel_score_audit WHERE lesson_no='KT1001'", Integer.class));

        // 阶段四：学院输新核对码核对新版纸质单 -> 教务处重新发布 -> 更正申请自动置 2 生效并刷新 GPA、通知学生
        asCollege();
        List<Map<String, Object>> auditPending = service.auditPending(TERM);
        assertEquals(1, ((Number) auditPending.get(0).get("correction_count")).intValue());
        service.audit("KT1001", code2, 1, "新版纸质成绩单核对一致");

        asOffice();
        List<Map<String, Object>> pubPending = service.publishPending(TERM);
        assertEquals(1, ((Number) pubPending.get(0).get("correction_count")).intValue());
        Map<String, Object> secondPub = service.publish("KT1001");
        assertEquals(1, secondPub.get("published"));
        assertEquals(1, secondPub.get("finalized_corrections"));

        assertEquals(2, jdbc.queryForObject("SELECT apply_status FROM sel_score_correction WHERE apply_id=?", Integer.class, applyId));
        assertEquals("E003", jdbc.queryForObject("SELECT auditor_id FROM sel_score_correction WHERE apply_id=?", String.class, applyId));
        assertNotNull(jdbc.queryForObject("SELECT reviewed_at FROM sel_score_correction WHERE apply_id=?", Object.class, applyId));
        assertEquals(0, new java.math.BigDecimal("91.50").compareTo(
                jdbc.queryForObject("SELECT new_score FROM sel_score_correction WHERE apply_id=?", java.math.BigDecimal.class, applyId)));
        assertEquals(0, new java.math.BigDecimal("4.00").compareTo(
                jdbc.queryForObject("SELECT gpa FROM sel_stu_gpa WHERE student_id='S001'", java.math.BigDecimal.class)));

        asStudent("S001");
        List<Map<String, Object>> stuNotices = correctionService.listMyCorrectionResults(TERM);
        assertEquals(1, stuNotices.size());
        assertEquals(0, new java.math.BigDecimal("91.50").compareTo(new java.math.BigDecimal(String.valueOf(stuNotices.get(0).get("new_score")))));
        assertEquals(0, new java.math.BigDecimal("4.00").compareTo(new java.math.BigDecimal(String.valueOf(stuNotices.get(0).get("new_grade_point")))));
    }

    @Test
    void gradeCorrectionCollegeRejectKeepsPublishedScoreLocked() {
        Long sc1 = jdbc.queryForObject("SELECT sc_id FROM sel_sc WHERE student_id='S001' AND tc_id='TC1'", Long.class);
        jdbc.update("INSERT INTO sel_score(sc_id,exam_type,score_type,usual_ratio,usual_score,exam_score,total_score,grade_point,is_final,score_status,input_teacher_id,submitted_at,published_at) VALUES(?,1,1,30,80,80,80,3.00,1,3,'E001',NOW(),NOW())", sc1);
        Long scoreId = jdbc.queryForObject("SELECT score_id FROM sel_score WHERE sc_id=?", Long.class, sc1);

        asTeacher();
        Map<String, Object> applied = correctionService.submitCorrection(Map.of(
                "score_id", scoreId, "expected_score", 80.0, "new_score", 88.0, "apply_reason", "复核加分"));
        long applyId = Long.parseLong(String.valueOf(applied.get("apply_id")));

        asCollege();
        assertEquals("OPINION_REQUIRED", assertThrows(BizException.class,
                () -> correctionService.collegeDecision(applyId, 2, "  ")).getCode());
        Map<String, Object> rej = correctionService.collegeDecision(applyId, 2, "佐证材料不足");
        assertEquals(3, rej.get("apply_status"));
        assertEquals(3, jdbc.queryForObject("SELECT score_status FROM sel_score WHERE score_id=?", Integer.class, scoreId));
        assertEquals(1, jdbc.queryForObject("SELECT is_final FROM sel_score WHERE score_id=?", Integer.class, scoreId));
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

    @Test
    void stoppedClassIsExcludedFromGradeEntryAndMarkedNoWorkload() {
        // 将 TC2（课堂 KT1002）标记为因人数不足停开（tc_status='2'）
        jdbc.update("UPDATE aca_teaching_class SET tc_status='2', no_open_reason='选课人数不足6人停开' WHERE tc_id='TC2'");

        // 1. 教师“我的课堂”只返回正常开课的 KT1001，不再包含已停开的 KT1002
        List<Map<String, Object>> active = service.myClasses(TERM, null);
        assertEquals(1, active.size());
        assertEquals("KT1001", active.get(0).get("lesson_no"));

        // 2. 已停开课程只读清单中展示 TC2，并明确标识不计入工作量
        List<Map<String, Object>> stopped = service.stoppedClasses(TERM, null);
        assertEquals(1, stopped.size());
        assertEquals("TC2", stopped.get(0).get("tc_id"));
        assertEquals("选课人数不足6人停开", stopped.get(0).get("no_open_reason"));
        assertEquals(false, stopped.get(0).get("workload_counted"));

        // 3. 尝试直接打开、保存或提交已停开课堂均被后端拒绝
        BizException ex = assertThrows(BizException.class, () -> service.roster("KT1002"));
        assertEquals("CLASS_STOPPED", ex.getCode());
        assertTrue(ex.getMessage().contains("停开"));
        assertTrue(ex.getMessage().contains("工作量"));
        assertThrows(BizException.class, () -> service.saveGrades("KT1002", List.of(), 30));
        assertThrows(BizException.class, () -> service.submit("KT1002"));
    }

    private static String readSheetXml(byte[] xlsx) throws Exception {
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.getName().endsWith("sheet1.xml")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
