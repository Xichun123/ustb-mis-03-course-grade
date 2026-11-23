-- =====================================================================
-- 03组选课及成绩管理：v8设计稿升级到v9设计稿（朱鳞太，2026-11-23）
-- 依据：数据字典_03组共享表_朱鳞太_v9_设计稿.xlsx
-- 升级内容（最小改动）：
--   1. 成绩录入以课堂号为主：几个教学班合成一个大课堂，课堂内的不同行政班、重修、辅修学生
--      用一个课堂号统一管理成绩：
--      - 新增第 14 张表 lesson_class 课堂组成表（2列，0 CHECK）
--      - score_audit.tc_id 改为 lesson_no（课堂号），按课堂整体提交与核对（列数不变）
--   2. sc.study_type 增加取值 3辅修（含双学位）；辅修成绩不计入主修阶段统计（改 1 条 CHECK）
--   3. score.usual_ratio、score.total_score 只改列注释（录入时校验一次最低占比，总评只校验输入规范）
-- 目标：MySQL 8.0.16+；先执行基线、v6至v7、v7至v8 三个脚本。
-- 升级后合计：14张表、134字段、82条CHECK约束（原v8为13表132字段82条CHECK）。
-- 若已执行“建表SQL_组间集成外键_可选_朱鳞太_v1.sql”中的 fk_score_audit_tc，须先执行：
--   ALTER TABLE score_audit DROP FOREIGN KEY fk_score_audit_tc;
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 一、新增第 14 张表：课堂组成表 lesson_class
-- ---------------------------------------------------------------------
CREATE TABLE lesson_class (
  tc_id     VARCHAR(20) NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id（第2组）；一个教学班只属于一个课堂',
  lesson_no VARCHAR(20) NOT NULL COMMENT '课堂号，如 KT202610001（KT+学年起始年+学期+4位流水）；同一课堂号下的教学班须同学期、同课程、同任课教师',
  PRIMARY KEY (tc_id),
  KEY idx_lesson_class_no (lesson_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '课堂组成表（业务③）；选课结束后系统按排课结果建议合并、教务处确认，单独上课的教学班也单独成一个课堂';

-- ---------------------------------------------------------------------
-- 二、成绩审核记录表 score_audit：tc_id → lesson_no（按课堂整体提交与核对）
-- ---------------------------------------------------------------------
ALTER TABLE score_audit
  CHANGE tc_id lesson_no VARCHAR(20) NOT NULL
    COMMENT '课堂号，按课堂整体提交与核对（含该课堂全部教学班的不同行政班、重修、辅修学生）；关联 lesson_class.lesson_no',
  RENAME INDEX uk_score_audit_tc_submit TO uk_score_audit_lesson_submit,
  RENAME INDEX idx_score_audit_tc_time TO idx_score_audit_lesson_time;

-- ---------------------------------------------------------------------
-- 三、选课记录表 sc：修读类型增加 3辅修（含双学位）
-- ---------------------------------------------------------------------
ALTER TABLE sc
  DROP CHECK ck_sc_study_type;
ALTER TABLE sc
  MODIFY study_type TINYINT NOT NULL DEFAULT 1
    COMMENT '修读类型：1初修 2重修 3辅修（含双学位）；辅修成绩不计入主修阶段的已获学分、GPA、加权平均分和排名',
  ADD CONSTRAINT ck_sc_study_type CHECK (study_type IN (1, 2, 3));

-- ---------------------------------------------------------------------
-- 四、成绩表 score：只改列注释
-- ---------------------------------------------------------------------
ALTER TABLE score
  MODIFY usual_ratio TINYINT NOT NULL
    COMMENT '平时成绩占比 0~100；同一课堂号、同一考试类别统一设定；最低占比（系统参数，现为30%）只在录入保存时校验一次',
  MODIFY total_score DECIMAL(4,1) NULL
    COMMENT '总评成绩 0~100，百分制按占比自动计算；计算前只校验输入数据规范，不再复验平时占比';

-- 业务规则说明（应用层与事务保证）：
-- 1. 课堂合并：选课结束、录分开始前，系统按第2组排课结果（同课程、同教师、同上课时间地点）建议合并，
--    教务处确认后写 lesson_class；保存时校验同一 lesson_no 下全部教学班的 term、course_id、teacher_id 一致；
--    课堂内已有成绩后不允许再调整组成。
-- 2. 教师录分入口：按 teaching_class.teacher_id 列出本人课堂（lesson_no，本学期/往届），教师点选后录分；
--    往届课堂只读，补考（score.exam_type=2）可在原课堂下录入；服务端仍校验课堂归属。
-- 3. 录分名单 = 该课堂全部教学班中 select_status=1 的 sc 记录，不按行政班或修读类型拆分。
-- 4. 总评计算与提交：只校验输入规范（分项齐全、0~100且最多1位小数、等级合法、按 exam_flag 处理），
--    不再复验最低平时占比；提交时按 lesson_no 新增 1 条 score_audit 批次并生成随机码。
-- 5. 学业统计（4.3）：stu_gpa、stu_term_gpa 计算时排除 sc.study_type=3（辅修）的成绩。
