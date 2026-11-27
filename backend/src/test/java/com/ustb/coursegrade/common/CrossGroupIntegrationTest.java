package com.ustb.coursegrade.common;

import com.ustb.coursegrade.common.auth.CurrentUser;
import com.ustb.coursegrade.common.auth.RoleEnum;
import com.ustb.coursegrade.common.auth.UserContext;
import com.ustb.coursegrade.module.prep.PrepService;
import com.ustb.coursegrade.module.score.ScoreService;
import com.ustb.coursegrade.module.selection.SelectionCatalogProperties;
import com.ustb.coursegrade.module.selection.SelectionService;
import com.ustb.coursegrade.module.stats.StatsService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 全班 8 个业务组 + 公共主数据跨组联动端到端隔离内存库测试。 */
class CrossGroupIntegrationTest {

    private static final String TERM = "2026-2027-1";

    private JdbcTemplate jdbc;
    private CrossGroupDataService crossGroup;
    private PrepService prepService;
    private SelectionService selectionService;
    private ScoreService scoreService;
    private StatsService statsService;

    @BeforeEach
    void setup() throws IOException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);

        for (String ddl : List.of(
                // 公共主数据与字典（第 16 组）
                "CREATE TABLE org_organization(org_id VARCHAR(6) PRIMARY KEY,org_name VARCHAR(100),college_id VARCHAR(4))",
                "CREATE TABLE sys_person(person_id VARCHAR(20) PRIMARY KEY,person_name VARCHAR(100))",
                "CREATE TABLE al_dict_type(dict_type VARCHAR(40) PRIMARY KEY,type_name VARCHAR(100),is_system TINYINT DEFAULT 0,dict_status VARCHAR(2) DEFAULT '01',remark VARCHAR(200))",
                "CREATE TABLE al_dict_item(dict_id BIGINT AUTO_INCREMENT PRIMARY KEY,dict_type VARCHAR(40),item_code VARCHAR(100),item_name VARCHAR(200),sort_no INT DEFAULT 0,version VARCHAR(40),effective_from DATE,item_status VARCHAR(2) DEFAULT '01')",
                // 第 1 组：学籍、注册、辅修、转专业
                "CREATE TABLE aca_class(class_id VARCHAR(10) PRIMARY KEY,class_name VARCHAR(50))",
                "CREATE TABLE std_student(student_id VARCHAR(20) PRIMARY KEY,person_id VARCHAR(20),class_id VARCHAR(10),major_id VARCHAR(6),org_id VARCHAR(6),grade VARCHAR(40),enrollment_year INT,student_type VARCHAR(2),student_status VARCHAR(2))",
                "CREATE TABLE std_registration(student_id VARCHAR(20),term VARCHAR(11),enrollment_status VARCHAR(20),payment_status VARCHAR(20),info_confirm VARCHAR(20),registration_status VARCHAR(20),registration_time TIMESTAMP,abnormal_reason VARCHAR(200),PRIMARY KEY(student_id,term))",
                "CREATE TABLE std_minor_degree(apply_id VARCHAR(20) PRIMARY KEY,person_id VARCHAR(20),apply_category VARCHAR(30),target_major VARCHAR(60),apply_status VARCHAR(10),final_result VARCHAR(30),final_opinion VARCHAR(200),submitted_at TIMESTAMP)",
                "CREATE TABLE std_major_transfer(transfer_id VARCHAR(20) PRIMARY KEY,student_id VARCHAR(20),application_term VARCHAR(11),current_major_id VARCHAR(6),target_major_id VARCHAR(6),weighted_average_score DECIMAL(5,2),current_major_rank INT,current_major_rank_total INT,application_status VARCHAR(20),admission_result VARCHAR(20),decision_date DATE,effective_term VARCHAR(11),submitted_at TIMESTAMP)",
                // 第 2 组：学期、专业、培养方案、课程、教学班、排课、调课、冲突、通识、双学位、学分转换
                "CREATE TABLE aca_term(term VARCHAR(11) PRIMARY KEY,academic_year VARCHAR(20),term_no INT,start_date DATE,end_date DATE,term_status VARCHAR(10))",
                "CREATE TABLE aca_major(major_id VARCHAR(6) PRIMARY KEY,major_name VARCHAR(100),org_id VARCHAR(6),college_id VARCHAR(4),duration INT DEFAULT 4,degree_name VARCHAR(20),min_credit DECIMAL(5,1))",
                "CREATE TABLE aca_major_program(major_id VARCHAR(6),grade VARCHAR(40),program_type VARCHAR(20),version VARCHAR(20),total_credit DECIMAL(5,1),credit_rule VARCHAR(200),goal_desc VARCHAR(300),plan_status VARCHAR(10),dual_quota INT,published_at TIMESTAMP,review_opinion VARCHAR(200),PRIMARY KEY(major_id,grade,program_type))",
                "CREATE TABLE aca_course(course_id VARCHAR(10) PRIMARY KEY,course_name VARCHAR(100),course_type VARCHAR(2),credit DECIMAL(5,1),org_id VARCHAR(6))",
                "CREATE TABLE aca_course_id(course_id VARCHAR(10) PRIMARY KEY,assess_type VARCHAR(20),pre_course_id VARCHAR(40))",
                "CREATE TABLE aca_program_course(major_id VARCHAR(6),grade VARCHAR(40),program_type VARCHAR(20),course_id VARCHAR(10),course_type VARCHAR(2),credit DECIMAL(5,1),open_semester INT,PRIMARY KEY(major_id,grade,program_type,course_id))",
                "CREATE TABLE aca_teaching_class(tc_id VARCHAR(20) PRIMARY KEY,term VARCHAR(11),course_id VARCHAR(10),emp_id VARCHAR(10),class_name VARCHAR(50),class_ids VARCHAR(100),capacity INT,actual_num INT DEFAULT 0,selected_count INT DEFAULT 0,hours DECIMAL(18,2) DEFAULT 48.00,week_count INT DEFAULT 16,teach_type VARCHAR(20) DEFAULT '1',tc_type VARCHAR(20) DEFAULT '1',tc_status VARCHAR(2) DEFAULT '4',no_open_reason VARCHAR(100),open_lower INT DEFAULT 6)",
                "CREATE TABLE aca_schedule_result(sched_id VARCHAR(14) PRIMARY KEY,tc_id VARCHAR(20),week_range VARCHAR(20),week_day VARCHAR(20),start_slot INT,end_slot INT,room_id VARCHAR(30),sched_status VARCHAR(2) DEFAULT '1')",
                "CREATE TABLE aca_adjust_record(adjust_id VARCHAR(20) PRIMARY KEY,sched_id VARCHAR(14),adjust_type VARCHAR(20),old_value VARCHAR(100),new_value VARCHAR(100),reason VARCHAR(200),approve_status VARCHAR(10),operated_at TIMESTAMP,valid_from DATE,approve_opinion VARCHAR(200))",
                "CREATE TABLE aca_schedule_conflict(conflict_id VARCHAR(20) PRIMARY KEY,tc_id VARCHAR(20),other_tc_id VARCHAR(20),conflict_type VARCHAR(20),conflict_desc VARCHAR(200),suggest_plan VARCHAR(200),found_time TIMESTAMP,handle_status VARCHAR(10))",
                "CREATE TABLE aca_general_plan(plan_id VARCHAR(20) PRIMARY KEY,school_year VARCHAR(20),course_id VARCHAR(10),plan_category VARCHAR(50))",
                "CREATE TABLE aca_dual_enroll(enroll_id VARCHAR(20) PRIMARY KEY,student_id VARCHAR(20),major_id VARCHAR(6),enroll_year VARCHAR(10),audit_status VARCHAR(10),finish_status VARCHAR(10),enroll_date DATE,remark VARCHAR(200))",
                "CREATE TABLE aca_credit_apply(apply_id VARCHAR(20) PRIMARY KEY,student_id VARCHAR(20),from_major VARCHAR(6),to_major VARCHAR(6),apply_credit DECIMAL(5,1),apply_date DATE,audit_status VARCHAR(10),audit_opinion VARCHAR(200),audit_date DATE)",
                "CREATE TABLE aca_credit_item(apply_id VARCHAR(20),seq_no INT,source_course_id VARCHAR(10),source_credit DECIMAL(5,1),target_course_id VARCHAR(10),target_credit DECIMAL(5,1),item_status VARCHAR(10),PRIMARY KEY(apply_id,seq_no))",
                // 第 4 组：师资、请假、出国
                "CREATE TABLE hr_employee(emp_id VARCHAR(10) PRIMARY KEY,person_id VARCHAR(20),emp_name VARCHAR(50),title_code VARCHAR(20),edu_level VARCHAR(20),emp_status VARCHAR(10),org_id VARCHAR(6))",
                "CREATE TABLE hr_leave(leave_id VARCHAR(20) PRIMARY KEY,emp_id VARCHAR(10),leave_type VARCHAR(30),start_at DATE,end_at DATE,days DECIMAL(5,1),reason VARCHAR(200),leave_status VARCHAR(10))",
                "CREATE TABLE hr_abroad(abroad_id VARCHAR(20) PRIMARY KEY,emp_id VARCHAR(10),abroad_type VARCHAR(30),country VARCHAR(50),start_date DATE,end_date DATE,approve_doc VARCHAR(50),apply_status VARCHAR(10))",
                // 第 5 组：财务收费项目与欠费账单
                "CREATE TABLE fin_charge_item(charge_item_id VARCHAR(20) PRIMARY KEY,charge_item_name VARCHAR(100))",
                "CREATE TABLE fin_charge(charge_no DECIMAL(18,2) PRIMARY KEY,student_id VARCHAR(20),charge_item DECIMAL(18,2),charge_period DECIMAL(18,2),receivable_amount DECIMAL(18,2),received_amount DECIMAL(18,2),arrears_amount DECIMAL(18,2),pay_method DECIMAL(18,2),invoice_no VARCHAR(50),audit_status VARCHAR(10))",
                // 第 7 组：教学楼与教室资产
                "CREATE TABLE ast_building(building_id VARCHAR(20) PRIMARY KEY,building_name VARCHAR(100),address VARCHAR(200))",
                "CREATE TABLE aca_room(room_id VARCHAR(20) PRIMARY KEY,room_code VARCHAR(30),room_name VARCHAR(60),building_id VARCHAR(20),floor INT,room_number VARCHAR(20),room_type VARCHAR(10),capacity INT,has_projector VARCHAR(2),computer_num INT,equip_desc VARCHAR(200),room_status VARCHAR(10))",
                // 第 9 组：实验室、封闭维护、预约
                "CREATE TABLE lab_laboratory(lab_id VARCHAR(20) PRIMARY KEY,lab_name VARCHAR(100),room_id VARCHAR(20),manager_emp_id VARCHAR(10),capacity INT,open_time VARCHAR(20),close_time VARCHAR(20),lab_status VARCHAR(10),building VARCHAR(60),floor INT,room_number VARCHAR(20),location VARCHAR(100),lab_type VARCHAR(30),description VARCHAR(200))",
                "CREATE TABLE lab_closure(closure_id VARCHAR(20) PRIMARY KEY,lab_id VARCHAR(20),start_at VARCHAR(30),end_at VARCHAR(30),reason VARCHAR(200),closure_status VARCHAR(10))",
                "CREATE TABLE lab_reservation(reservation_id VARCHAR(20) PRIMARY KEY,lab_id VARCHAR(20),date DATE,start_at VARCHAR(20),end_at VARCHAR(20),purpose VARCHAR(200),participants INT,reservation_status VARCHAR(10),course_id VARCHAR(10),course_name VARCHAR(100),course_teacher VARCHAR(50),week_no INT)",
                // 第 13 组：研究生培养全链路
                "CREATE TABLE grad_direction(direction_id VARCHAR(20) PRIMARY KEY,direction_name VARCHAR(100))",
                "CREATE TABLE grad_training_scheme(scheme_id VARCHAR(50) PRIMARY KEY,major_id VARCHAR(6),grade VARCHAR(20),level VARCHAR(20),version_no VARCHAR(20),min_credit DECIMAL(5,1),max_credit DECIMAL(5,1),publish_status VARCHAR(10))",
                "CREATE TABLE grad_scheme_requirement(scheme_line_id VARCHAR(30) PRIMARY KEY,scheme_id VARCHAR(50),group_code VARCHAR(20),min_credit DECIMAL(5,1),max_credit DECIMAL(5,1),min_count INT)",
                "CREATE TABLE grad_scheme_assignment(assignment_id VARCHAR(50) PRIMARY KEY,student_id VARCHAR(20),scheme_id VARCHAR(50),direction_id VARCHAR(20),valid_from DATE,valid_to DATE)",
                "CREATE TABLE grad_scheme_course(scheme_course_id VARCHAR(50) PRIMARY KEY,scheme_id VARCHAR(50),course_id VARCHAR(10),direction_id VARCHAR(20),course_category INT,suggest_term INT)",
                "CREATE TABLE grad_training_plan(plan_id VARCHAR(30) PRIMARY KEY,student_id VARCHAR(20),scheme_id VARCHAR(50),version_no INT,plan_status VARCHAR(10))",
                "CREATE TABLE grad_plan_course(plan_id VARCHAR(30),course_id VARCHAR(10),course_source VARCHAR(20),planned_term VARCHAR(20),credit_snapshot DECIMAL(5,1),PRIMARY KEY(plan_id,course_id))",
                "CREATE TABLE grad_student_mentor(mentor_rel_id VARCHAR(30) PRIMARY KEY,student_id VARCHAR(20),mentor_user_id VARCHAR(20),is_primary INT,valid_from DATE,valid_to DATE)",
                // 第 15 组：对外交流、返校备案、外校成绩单、留学生、接收导师意见、教师出访
                "CREATE TABLE v_exchange_student(temp_student_no VARCHAR(20),student_name VARCHAR(50),sender_school_name VARCHAR(100),term_ref VARCHAR(11),college_ref VARCHAR(4),major_ref VARCHAR(6),start_on DATE,end_on DATE,status_code VARCHAR(20))",
                "CREATE TABLE exg_admission(admission_id VARCHAR(20) PRIMARY KEY,apply_id VARCHAR(20))",
                "CREATE TABLE exg_temp_student(temp_student_id VARCHAR(20) PRIMARY KEY,admission_id VARCHAR(20))",
                "CREATE TABLE exg_supervisor_opinion(opinion_id VARCHAR(20) PRIMARY KEY,apply_id VARCHAR(20),emp_id VARCHAR(10),emp_name VARCHAR(50),college_name VARCHAR(100),accept_opinion VARCHAR(200),course_plan VARCHAR(200),submitted_at TIMESTAMP)",
                "CREATE TABLE exg_is_student(student_id VARCHAR(20) PRIMARY KEY,person_name VARCHAR(100),nationality VARCHAR(50),passport_no VARCHAR(40),college_name VARCHAR(100),major_name VARCHAR(100),class_name VARCHAR(50),degree_level VARCHAR(20),enroll_date DATE,expected_graduation_date DATE,scholarship_program VARCHAR(100),register_status VARCHAR(20),student_status VARCHAR(20))",
                "CREATE TABLE exg_ob_teacher_status(status_id VARCHAR(20) PRIMARY KEY,emp_id VARCHAR(10),trip_id VARCHAR(30),current_status VARCHAR(10),effective_time DATE,end_at DATE)",
                "CREATE TABLE v_exchange_credit_ready(application_no VARCHAR(30),student_id VARCHAR(20),term_ref VARCHAR(11),partner_school_snapshot VARCHAR(100),ready_flag INT,exchange_type VARCHAR(20),actual_return_date DATE)",
                "CREATE TABLE exg_vst_foreign_transcript(foreign_transcript_id VARCHAR(30) PRIMARY KEY,student_id VARCHAR(20),partner_school VARCHAR(100),study_term VARCHAR(11),foreign_course_id VARCHAR(30),foreign_course_name VARCHAR(100),foreign_credit DECIMAL(5,1),foreign_score VARCHAR(20),issue_date DATE)",
                // 本组核心 sel_* 表
                "CREATE TABLE sel_lesson_class(tc_id VARCHAR(20) PRIMARY KEY,lesson_no VARCHAR(20))",
                "CREATE TABLE sel_select_round(round_id INT AUTO_INCREMENT PRIMARY KEY,term VARCHAR(11),round_name VARCHAR(30),round_type INT,target_level INT,start_at TIMESTAMP,end_at TIMESTAMP,max_credit DECIMAL(5,1),select_mode INT,allow_retake INT,retake_ignore_conflict INT,round_status INT,notice_content VARCHAR(500),published_at TIMESTAMP,approver_id VARCHAR(10),approved_at TIMESTAMP,approve_opinion VARCHAR(200))",
                "CREATE TABLE sel_round_class(round_id INT,tc_id VARCHAR(20),PRIMARY KEY(round_id,tc_id))",
                "CREATE TABLE sel_select_limit(limit_id INT AUTO_INCREMENT PRIMARY KEY,tc_id VARCHAR(20),limit_type INT,limit_value VARCHAR(20))",
                "CREATE TABLE sel_sc(sc_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,study_type INT DEFAULT 1,is_exempt_attend INT DEFAULT 0,stu_level VARCHAR(20),stat_stage INT DEFAULT 1,priority INT,select_status INT DEFAULT 1,select_time TIMESTAMP,drop_time TIMESTAMP)",
                "CREATE TABLE sel_select_log(log_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),tc_id VARCHAR(20),round_id INT,action_type INT,operator_id VARCHAR(40),operated_at TIMESTAMP,remark VARCHAR(500))",
                "CREATE TABLE sel_score(score_id BIGINT AUTO_INCREMENT PRIMARY KEY,sc_id BIGINT,exam_type INT DEFAULT 1,exam_flag INT DEFAULT 0,score_type INT DEFAULT 1,usual_ratio DECIMAL(5,2),usual_score DECIMAL(5,2),exam_score DECIMAL(5,2),total_score DECIMAL(5,2),score_level VARCHAR(10),grade_point DECIMAL(5,2),is_final INT DEFAULT 1,score_status INT DEFAULT 3,published_at TIMESTAMP)",
                "CREATE TABLE sel_score_correction(correction_id BIGINT AUTO_INCREMENT PRIMARY KEY,score_id BIGINT,apply_status INT)",
                "CREATE TABLE sel_exchange_score_convert(convert_id BIGINT AUTO_INCREMENT PRIMARY KEY,student_id VARCHAR(20),exchange_apply_id VARCHAR(30),term VARCHAR(11),stat_stage INT DEFAULT 1,host_school VARCHAR(100),ext_course_code VARCHAR(30),ext_course_name VARCHAR(100),ext_credit DECIMAL(5,1),ext_score_raw VARCHAR(20),ext_scale_type INT DEFAULT 1,course_id VARCHAR(10),score_type INT DEFAULT 1,converted_score DECIMAL(5,2),converted_level VARCHAR(10),grade_point DECIMAL(5,2),is_final INT DEFAULT 0,proof_no VARCHAR(50),version_no INT DEFAULT 1,audit_status INT DEFAULT 0,submitted_at TIMESTAMP,school_audit_time TIMESTAMP)",
                "CREATE TABLE sel_teach_eval(sc_id BIGINT PRIMARY KEY,eval_score DECIMAL(5,2),eval_comment VARCHAR(200),eval_time TIMESTAMP)",
                "CREATE TABLE sel_tc_eval_result(tc_id VARCHAR(20) PRIMARY KEY,eval_num INT,avg_eval_score DECIMAL(5,2),calc_time TIMESTAMP)",
                "CREATE TABLE sel_stu_gpa(student_id VARCHAR(20) PRIMARY KEY,earned_credit DECIMAL(5,1),failed_credit DECIMAL(5,1),gpa DECIMAL(5,2),avg_score DECIMAL(5,2),major_rank INT,major_rank_total INT,updated_at TIMESTAMP)",
                "CREATE TABLE sel_stu_term_gpa(student_id VARCHAR(20),term VARCHAR(11),term_credit DECIMAL(5,1),term_failed_credit DECIMAL(5,1),updated_at TIMESTAMP,PRIMARY KEY(student_id,term))"
        )) {
            jdbc.execute(ddl);
        }

        // 基础种子数据
        jdbc.update("INSERT INTO org_organization VALUES('ORG001','经济管理学院','0001'),('ORG002','计通学院','0002')");
        jdbc.update("INSERT INTO al_dict_type VALUES('system_param','系统参数',1,'01',NULL),('HR_TITLE','职称',1,'01',NULL)");
        jdbc.update("INSERT INTO al_dict_item(dict_type,item_code,item_name,item_status) VALUES"
                + "('system_param','current_term','2026-2027-1','01'),"
                + "('system_param','block_selection_on_arrears','0','01'),"
                + "('system_param','min_open_students','6','01'),"
                + "('HR_TITLE','PROF','教授','01'),"
                + "('HR_TITLE','ASSOC_PROF','副教授','01')");

        jdbc.update("INSERT INTO aca_term VALUES('2026-2027-2','2026-2027',2,'2027-02-22','2027-07-04','00'),"
                + "('2026-2027-1','2026-2027',1,'2026-09-01','2027-01-15','01'),"
                + "('2025-2026-2','2025-2026',2,'2026-02-24','2026-07-05','02')");

        jdbc.update("INSERT INTO hr_employee VALUES('T001','PT1','王明远','PROF','博士','01','ORG001'),"
                + "('T002','PT2','陈志强','ASSOC_PROF','博士','01','ORG002')");
        jdbc.update("INSERT INTO hr_leave VALUES('LV1','T002','病假','2026-11-28','2026-12-02',5.0,'住院复查','02')");
        jdbc.update("INSERT INTO hr_abroad VALUES('AB1','T002','学术会议','新加坡','2026-12-10','2026-12-15','北科外〔2026〕18号','02')");
        jdbc.update("INSERT INTO exg_ob_teacher_status VALUES('OB1','T002','TRIP-2026-08','02','2026-12-10','2026-12-15')");

        jdbc.update("INSERT INTO ast_building VALUES('BLD_YF','逸夫楼','学院路30号'),('BLD_LAB','信息实验楼','学院路30号')");
        jdbc.update("INSERT INTO aca_room VALUES"
                + "('RM_YF402','YF-402','逸夫楼402','BLD_YF',4,'402','01',45,'1',0,'高清投影与录播','01'),"
                + "('RM_LABB201','LAB-B201','信息实验楼B201','BLD_LAB',2,'B201','02',60,'1',60,'60台Linux实验机','01')");
        jdbc.update("INSERT INTO lab_laboratory VALUES('LAB_B201','大数据系统实验室','RM_LABB201','T001',60,'08:00','21:00','01','信息实验楼',2,'B201','B201','专业实验','支持实验教学')");
        jdbc.update("INSERT INTO lab_closure VALUES('CL1','LAB_B201','2026-12-05 08:00','2026-12-06 18:00','机房电路检修','01')");

        jdbc.update("INSERT INTO aca_major VALUES('120102','信息管理与信息系统','ORG001','0001',4,'管理学学士',10.0),"
                + "('080901','计算机科学与技术','ORG002','0002',4,'工学学士',10.0),"
                + "('120100','管理科学与工程','ORG001','0001',3,'管理学硕士',5.0)");
        jdbc.update("INSERT INTO aca_major_program VALUES('120102','2024','1','2024版',10.0,'11:2.0;12:3.0;16:2.0','培养数智化复合型管理人才','01',20,CURRENT_TIMESTAMP,'审核通过')");
        jdbc.update("INSERT INTO aca_class VALUES('CLS241','信管241'),('CLS26M','管科硕261')");

        jdbc.update("INSERT INTO aca_course VALUES"
                + "('C001','数据结构与算法','12',3.0,'ORG002'),"
                + "('C002','艺术与人文导论','15',2.0,'ORG001'),"
                + "('CMIN','辅修金融学','14',3.0,'ORG001'),"
                + "('G001','智能决策分析','13',3.0,'ORG001')");
        jdbc.update("INSERT INTO aca_course_id VALUES('C001','1',NULL),('C002','2',NULL),('CMIN','1',NULL),('G001','1',NULL)");
        jdbc.update("INSERT INTO aca_general_plan VALUES('GP1','2026-2027','C002','人文艺术素养')");
        jdbc.update("INSERT INTO aca_program_course VALUES('120102','2024','1','C001','12',3.0,3)");

        jdbc.update("INSERT INTO aca_teaching_class(tc_id,term,course_id,emp_id,class_name,class_ids,capacity,actual_num,tc_type,tc_status) VALUES"
                + "('TC1',?,'C001','T002','数据结构01班','信管241',50,1,'1','4'),"
                + "('TC2',?,'C002','T001','通识01班','全校',60,0,'2','4'),"
                + "('TCMIN',?,'CMIN','T001','辅修金融01班','辅修班',40,0,'3','4'),"
                + "('TCG1',?,'G001','T001','研方向01班','管科硕261',30,0,'1','4')", TERM, TERM, TERM, TERM);
        jdbc.update("INSERT INTO sel_lesson_class VALUES('TC1','L001'),('TC2','L002')");
        jdbc.update("INSERT INTO aca_schedule_result VALUES"
                + "('SCH1','TC1','1-16','1',1,2,'逸夫楼402','1'),"
                + "('SCH2','TC2','1-16','2',3,4,'信息实验楼B201','1'),"
                + "('SCH3','TCMIN','1-16','3',5,6,'逸夫楼402','1'),"
                + "('SCH4','TCG1','1-16','4',1,2,'信息实验楼B201','1')");
        jdbc.update("INSERT INTO aca_adjust_record VALUES('ADJ1','SCH1','教室调整','逸夫楼401','逸夫楼402','多媒体升级','1',CURRENT_TIMESTAMP,'2026-11-20','批准')");
        jdbc.update("INSERT INTO aca_schedule_conflict VALUES('CNF1','TC1','TC99','ROOM','周一1-2节教室冲突','建议调至周三','2026-11-20 10:00:00','0')");

        // 学生（正常、欠费、未注册、无注册记录兼容、辅修获批、转专业、研究生、留学生、来校交换生）
        jdbc.update("INSERT INTO sys_person VALUES"
                + "('P1','张三'),('P2','林思远'),('P3','陈雨桐'),('P4','无记录生'),"
                + "('P5','赵子涵'),('P6','刘浩然'),('PM1','许嘉豪'),('PIS1','Sofia Lindqvist')");
        jdbc.update("INSERT INTO std_student VALUES"
                + "('U001','P1','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('U002','P2','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('U003','P3','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('U004','P4','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('U005','P5','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('U006','P6','CLS241','120102','ORG001','2024',2024,'01','01'),"
                + "('M001','PM1','CLS26M','120100','ORG001','2026',2026,'02','01'),"
                + "('IS001','PIS1','CLS241','120102','ORG001','2024',2024,'01','01')");

        jdbc.update("INSERT INTO std_registration VALUES"
                + "('U001',?,'在籍','已缴费','已确认','01',CURRENT_TIMESTAMP,NULL),"
                + "('U002',?,'在籍','欠费','已确认','01',CURRENT_TIMESTAMP,NULL),"
                + "('U003',?,'在籍','未缴费','未确认','02',NULL,'尚未到学院报到核验'),"
                + "('U005',?,'在籍','已缴费','已确认','01',CURRENT_TIMESTAMP,NULL)", TERM, TERM, TERM, TERM);
        jdbc.update("INSERT INTO fin_charge_item VALUES('101.00','学费'),('102.00','住宿费')");
        jdbc.update("INSERT INTO fin_charge VALUES"
                + "(202601.00,'U002',101.00,2026.00,5000.00,3800.00,1200.00,1.00,NULL,'01'),"
                + "(202602.00,'U001',101.00,2026.00,5000.00,5000.00,0.00,1.00,'INV01','01')");
        jdbc.update("INSERT INTO std_minor_degree VALUES('MN1','P5','辅修学位','金融学','03','通过','审核通过',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO std_major_transfer VALUES('TR1','U006','2025-2026-2','080901','120102',88.50,5,60,'03','同意转入','2026-07-01','2026-2027-1',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO aca_credit_apply VALUES('CA1','U006','080901','120102',3.0,'2026-09-05','1','同意学分转换','2026-09-10')");
        jdbc.update("INSERT INTO aca_credit_item VALUES('CA1',1,'C001',3.0,'C001',3.0,'1')");

        // 研究生全链路
        jdbc.update("INSERT INTO grad_direction VALUES('DIR01','大数据与智能决策')");
        jdbc.update("INSERT INTO grad_training_scheme VALUES('SCH_M','120100','2026','硕士','2026',5.0,32.0,'01')");
        jdbc.update("INSERT INTO grad_scheme_requirement VALUES('REQ1','SCH_M','CORE',3.0,NULL,1),('REQ2','SCH_M','DIR',2.0,NULL,1)");
        jdbc.update("INSERT INTO grad_scheme_assignment VALUES('ASN1','M001','SCH_M','DIR01','2026-01-01',NULL)");
        jdbc.update("INSERT INTO grad_scheme_course VALUES('GSC1','SCH_M','G001','DIR01',2,1)");
        jdbc.update("INSERT INTO grad_training_plan VALUES('PLAN1','M001','SCH_M',1,'01')");
        jdbc.update("INSERT INTO grad_plan_course VALUES('PLAN1','G001','SCHEME','2026-2027-1',3.0)");
        jdbc.update("INSERT INTO grad_student_mentor VALUES('GM1','M001','T001',1,'2026-09-01',NULL)");

        // 对外交流：留学生、来校交换生导师建议、返校备案视图、外校成绩单
        jdbc.update("INSERT INTO exg_is_student VALUES('IS001','Sofia Lindqvist','瑞典','SE9988','经济管理学院','信息管理与信息系统','信管241','本科','2024-09-01','2028-06-30','中国政府奖学金','01','01')");
        jdbc.update("INSERT INTO v_exchange_student VALUES('EX001','Alex Kim','首尔国立大学',?,'0001','120102','2026-09-01','2027-01-15','ACTIVE')", TERM);
        jdbc.update("INSERT INTO exg_admission VALUES('ADM1','APP1')");
        jdbc.update("INSERT INTO exg_temp_student VALUES('EX001','ADM1')");
        jdbc.update("INSERT INTO exg_supervisor_opinion VALUES('OPN1','APP1','T001','王明远','经济管理学院','同意接收','建议优先修读数据结构与算法',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO v_exchange_credit_ready VALUES"
                + "('EXAPP-INTL-1','U001',?,'新加坡国立大学',1,'INTL','2026-08-25'),"
                + "('EXAPP-INTL-2','U002',?,'慕尼黑工业大学',0,'INTL',NULL),"
                + "('EXAPP-DOM-1','U003',?,'清华大学',1,'DOMESTIC','2026-08-28')", TERM, TERM, TERM);
        jdbc.update("INSERT INTO exg_vst_foreign_transcript VALUES('FT1001','U001','新加坡国立大学',?,'CS2040','Data Structures',3.0,'A-','2026-08-18')", TERM);

        // 开放选课轮次
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO sel_select_round(round_id,term,round_name,round_type,target_level,start_at,end_at,max_credit,select_mode,allow_retake,retake_ignore_conflict,round_status,notice_content) "
                        + "VALUES(1,?,'2026秋正选',2,3,?,?,30,1,1,0,3,'测试轮次')",
                TERM, Timestamp.valueOf(now.minusDays(1)), Timestamp.valueOf(now.plusDays(7)));
        for (String tc : List.of("TC1", "TC2", "TCMIN", "TCG1")) {
            jdbc.update("INSERT INTO sel_round_class VALUES(1,?)", tc);
        }

        var demoConfig = new YamlPropertySourceLoader().load("selection-catalog-demo",
                new ClassPathResource("selection/catalog-demo.yml"));
        var catalog = new Binder(ConfigurationPropertySources.from(demoConfig))
                .bind("selection.catalog", Bindable.of(SelectionCatalogProperties.class)).get();

        crossGroup = new CrossGroupDataService(jdbc);
        prepService = new PrepService(jdbc, crossGroup);
        selectionService = new SelectionService(jdbc, catalog, crossGroup);
        scoreService = new ScoreService(jdbc, crossGroup);
        statsService = new StatsService(jdbc, crossGroup);
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        jdbc.execute("SHUTDOWN");
    }

    private void asOffice() {
        UserContext.set(new CurrentUser("off", "教务处", RoleEnum.ACADEMIC_OFFICE, null, "E900", null, null, null));
    }

    private void asStudent(String sid) {
        UserContext.set(new CurrentUser(sid, sid, RoleEnum.STUDENT, sid, null, null, "0001", null));
    }

    private void asTeacher(String empId) {
        UserContext.set(new CurrentUser(empId, empId, RoleEnum.TEACHER, null, empId, empId, "0002", null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dynamicTermsFromAcaTermAndCrossGroupOverviewWork() {
        Map<String, Object> termsPayload = crossGroup.getTermsPayload();
        assertEquals(TERM, termsPayload.get("current_term"));
        List<Map<String, Object>> termItems = (List<Map<String, Object>>) termsPayload.get("items");
        assertEquals(3, termItems.size());
        assertEquals("2026-2027-2", termItems.get(0).get("term"));
        assertTrue(termItems.stream().anyMatch(m -> TERM.equals(m.get("term")) && Boolean.TRUE.equals(m.get("is_current"))));

        Map<String, Object> overview = crossGroup.getCrossGroupOverview();
        Map<String, Object> summary = (Map<String, Object>) overview.get("summary");
        List<Map<String, Object>> groups = (List<Map<String, Object>>) overview.get("groups");
        assertEquals(8, summary.get("connected_business_groups"));
        assertEquals(9, groups.size());
        assertEquals(3, summary.get("bidirectional_groups"));
        assertTrue(((Number) summary.get("active_upstream_tables")).intValue() >= 30);
        assertTrue(((Number) summary.get("total_upstream_records")).longValue() > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void prepEnrichesRoomsLabsTeacherAlertsAdjustmentsConflictsAndGeneralPlan() {
        asOffice();
        PageResult<Map<String, Object>> page = prepService.listTeachingClasses(TERM, null, null, null, null, 1, 20);
        Map<String, Object> tc1 = page.getItems().stream().filter(r -> "TC1".equals(r.get("tc_id"))).findFirst().orElseThrow();
        Map<String, Object> tc2 = page.getItems().stream().filter(r -> "TC2".equals(r.get("tc_id"))).findFirst().orElseThrow();

        // TC1: 第 7 组教室超容（50 > 45）、第 4/15 组教师请假+出国+出访提醒、第 2 组调课与冲突
        assertEquals(true, tc1.get("room_over_capacity"));
        assertEquals("副教授", tc1.get("teacher_title"));
        List<Map<String, Object>> tc1Alerts = (List<Map<String, Object>>) tc1.get("teacher_alerts");
        assertEquals(3, tc1Alerts.size());
        assertEquals(1, ((List<?>) tc1.get("schedule_adjustments")).size());
        assertEquals(1, ((List<?>) tc1.get("schedule_conflicts")).size());

        // TC2: 第 9 组实验室 + 封闭提醒 + 第 2 组通识类别
        assertEquals("人文艺术素养", tc2.get("general_category"));
        Map<String, Object> labInfo = (Map<String, Object>) tc2.get("lab_info");
        assertNotNull(labInfo);
        assertEquals("大数据系统实验室", labInfo.get("lab_name"));
        assertEquals(1, ((List<?>) tc2.get("lab_closures")).size());

        // 排课概况含超容预警和冲突统计
        Map<String, Object> sync = prepService.getScheduleSyncSummary(TERM);
        assertEquals(1, sync.get("conflict_classes"));
        assertEquals(1, sync.get("over_capacity_classes"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectionEnforcesRegistrationArrearsSwitchMinorQualificationAndPersonalPlan() {
        // 1) 未注册学生 U003 被第 1 组学籍注册状态拦截
        asStudent("U003");
        Map<String, Object> u3Ctx = selectionService.context();
        Map<String, Object> u3Cross = (Map<String, Object>) u3Ctx.get("cross_group_status");
        assertEquals(true, ((Map<?, ?>) u3Cross.get("registration")).get("blocked"));
        BizException regEx = assertThrows(BizException.class,
                () -> selectionService.enroll(new SelectionService.Application(1, "TC2", 1, null, null, false)));
        assertTrue(regEx.getMessage().contains("注册异常"));

        // 2) 无注册记录学生 U004 兼容自动放行
        asStudent("U004");
        assertDoesNotThrow(() -> selectionService.enroll(new SelectionService.Application(1, "TC2", 1, null, null, false)));

        // 3) 欠费学生 U002：默认仅提醒放行，check 返回 finance_warning
        asStudent("U002");
        Map<String, Object> checkRes = selectionService.check(new SelectionService.Application(1, "TC2", 1, null, null, false));
        assertNotNull(checkRes.get("finance_warning"));
        assertEquals("1200.00", checkRes.get("total_arrears"));
        assertDoesNotThrow(() -> selectionService.enroll(new SelectionService.Application(1, "TC2", 1, null, null, false)));

        // 开启「欠费拦截选课」开关后，U002 选另一门课被拦截
        jdbc.update("UPDATE al_dict_item SET item_name = '1' WHERE dict_type = 'system_param' AND item_code = 'block_selection_on_arrears'");
        BizException finEx = assertThrows(BizException.class,
                () -> selectionService.enroll(new SelectionService.Application(1, "TC1", 1, null, null, false)));
        assertTrue(finEx.getMessage().contains("欠费"));
        jdbc.update("UPDATE al_dict_item SET item_name = '0' WHERE dict_type = 'system_param' AND item_code = 'block_selection_on_arrears'");

        // 4) 辅修资格校验：未获批的 U001 选辅修被拦截，已获批的 U005 放行
        asStudent("U001");
        BizException minorEx = assertThrows(BizException.class,
                () -> selectionService.enroll(new SelectionService.Application(1, "TCMIN", 3, null, null, false)));
        assertTrue(minorEx.getMessage().contains("辅修或双学位修读资格"));
        asStudent("U005");
        assertDoesNotThrow(() -> selectionService.enroll(new SelectionService.Application(1, "TCMIN", 3, null, null, false)));

        // 5) 研究生 M001 课程列表标注个人培养计划内，来校交换生 EX001 展示接收导师选课建议
        asStudent("M001");
        Map<String, Object> gradCourses = selectionService.courses(1, "智能决策", 1, 10, null);
        List<Map<String, Object>> gradItems = (List<Map<String, Object>>) gradCourses.get("items");
        assertEquals(1, gradItems.size());
        assertEquals(true, gradItems.get(0).get("in_personal_plan"));
        assertEquals(TERM, gradItems.get(0).get("planned_term"));

        asStudent("EX001");
        Map<String, Object> exCtx = selectionService.context();
        Map<String, Object> exCross = (Map<String, Object>) exCtx.get("cross_group_status");
        Map<String, Object> sup = (Map<String, Object>) exCross.get("exchange_supervisor");
        assertNotNull(sup);
        assertEquals("王明远", sup.get("emp_name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void scoreEnforcesExchangeReturnFilingAndEnrichesTeacherAlerts() {
        asOffice();
        List<Map<String, Object>> readyList = scoreService.listExchangeReadyStudents(null);
        assertEquals(3, readyList.size());
        assertTrue(readyList.stream().anyMatch(r -> "DOMESTIC".equals(r.get("exchange_type")) && Boolean.TRUE.equals(r.get("ready"))));

        List<Map<String, Object>> fts = scoreService.listForeignTranscripts("U001");
        assertEquals(1, fts.size());
        assertEquals("CS2040", fts.get(0).get("foreign_course_id"));

        // 未完成返校备案的 U002 被拦截
        Map<String, Object> unreadyBody = new LinkedHashMap<>();
        unreadyBody.put("student_id", "U002");
        unreadyBody.put("term", TERM);
        unreadyBody.put("host_school", "慕尼黑工业大学");
        unreadyBody.put("ext_course_name", "Algorithms");
        unreadyBody.put("ext_credit", 3.0);
        unreadyBody.put("ext_score_raw", "90");
        unreadyBody.put("course_id", "C001");
        unreadyBody.put("proof_no", "FT-BLOCK");
        unreadyBody.put("score_type", 1);
        unreadyBody.put("converted_score", 90);
        BizException ex = assertThrows(BizException.class, () -> scoreService.createExchange(unreadyBody));
        assertEquals("EXCHANGE_NOT_READY", ex.getCode());

        // 已完成国内访学返校备案的 U003 自动补齐合作院校与申请单号并登记成功
        Map<String, Object> readyBody = new LinkedHashMap<>();
        readyBody.put("student_id", "U003");
        readyBody.put("ext_course_name", "数据结构");
        readyBody.put("ext_credit", 3.0);
        readyBody.put("ext_score_raw", "92");
        readyBody.put("course_id", "C001");
        readyBody.put("proof_no", "FT-DOM-01");
        readyBody.put("score_type", 1);
        readyBody.put("converted_score", 92);
        Map<String, Object> created = scoreService.createExchange(readyBody);
        assertEquals("U003", created.get("student_id"));
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT exchange_apply_id, host_school, term FROM sel_exchange_score_convert WHERE proof_no = 'FT-DOM-01'");
        assertEquals("EXAPP-DOM-1", saved.get("exchange_apply_id"));
        assertEquals("清华大学", saved.get("host_school"));
        assertEquals(TERM, saved.get("term"));

        // 教师端课堂列表展示第 4/15 组教师职称与请假出访提醒
        asTeacher("T002");
        List<Map<String, Object>> classes = scoreService.myClasses(TERM, null);
        assertEquals(1, classes.size());
        assertEquals("陈志强", classes.get(0).get("teacher_name"));
        assertEquals("副教授", classes.get(0).get("teacher_title"));
        assertEquals(3, ((List<?>) classes.get(0).get("teacher_alerts")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void statsEnrichesMajorProgramTransferGraduateProfileInternationalTranscriptAndEvalTeacher() {
        asOffice();

        // 1) 转专业学生 U006：含本科培养方案总纲、分类学分要求与转专业/学分转换明细
        Map<String, Object> u6Prog = statsService.getAcademicProgress("U006");
        Map<String, Object> majorProg = (Map<String, Object>) u6Prog.get("major_program");
        assertNotNull(majorProg);
        assertEquals("培养数智化复合型管理人才", majorProg.get("goal_desc"));
        List<Map<String, Object>> cats = (List<Map<String, Object>>) u6Prog.get("categories");
        Map<String, Object> cat12 = cats.stream().filter(c -> "12".equals(c.get("course_type"))).findFirst().orElseThrow();
        assertEquals("3.0", cat12.get("program_min_credit"));
        Map<String, Object> transferInfo = (Map<String, Object>) u6Prog.get("transfer_info");
        assertNotNull(transferInfo);
        assertEquals(1, transferInfo.get("converted_course_count"));

        // 2) 研究生 M001：研究方向中文名 + 导师 + 分类学分要求 + 个人培养计划标注
        Map<String, Object> m1Prog = statsService.getAcademicProgress("M001");
        Map<String, Object> gp = (Map<String, Object>) m1Prog.get("graduate_profile");
        assertNotNull(gp);
        assertEquals("大数据与智能决策", gp.get("direction_name"));
        assertEquals(2, ((List<?>) gp.get("scheme_requirements")).size());
        assertEquals(1, ((List<?>) gp.get("mentors")).size());
        List<Map<String, Object>> planCourses = (List<Map<String, Object>>) m1Prog.get("plan_courses");
        assertTrue(planCourses.stream().anyMatch(pc -> "G001".equals(pc.get("course_id")) && Boolean.TRUE.equals(pc.get("in_personal_plan"))));

        // 3) 来华留学生 IS001：自动匹配中英文双语成绩单版式并展示国籍与奖学金
        Map<String, Object> tr = statsService.generateTranscriptPreview(Map.of("student_id", "IS001", "template", "AUTO"));
        assertEquals("EXCHANGE_BILINGUAL", tr.get("template"));
        assertEquals(true, tr.get("is_international_student"));
        Map<String, Object> stu = (Map<String, Object>) tr.get("student");
        assertEquals("瑞典", stu.get("nationality"));
        assertEquals("中国政府奖学金", stu.get("scholarship_program"));

        // 4) 评教汇总：附带第 4 组教师职称与学院
        jdbc.update("INSERT INTO sel_tc_eval_result VALUES('TC1',10,94.50,CURRENT_TIMESTAMP)");
        Map<String, Object> evalSummary = statsService.listEvaluationSummaries(TERM, null);
        List<Map<String, Object>> evalItems = (List<Map<String, Object>>) evalSummary.get("items");
        Map<String, Object> tc1Eval = evalItems.stream().filter(r -> "TC1".equals(r.get("tc_id"))).findFirst().orElseThrow();
        assertEquals("副教授", tc1Eval.get("teacher_title"));
        assertEquals("计通学院", tc1Eval.get("teacher_org_name"));
    }
}
