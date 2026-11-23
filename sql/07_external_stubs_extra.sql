-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 外部引用桩表补充脚本（仅本组开发联调用）
-- 文件：01_外部引用桩表补充_联调用.sql
-- 说明：
--   1. 先执行 02_第二阶段_设计/数据库设计/建表SQL_外部引用桩表_可选_朱鳞太_v1.sql
--      （建 student、course、program_course、teaching_class、t_employee、
--       grad_direction、scheme_assignment、scheme_course 共 8 张桩表）。
--   2. 本脚本补充联调必须用到但 v6 桩表未列出的辅助字段/桩表/视图：
--      - student 补充 major_id、college_id（选课限选与专业排名需要）
--      - course 补充 course_level（本科/研究生归属阶段判断需要）
--      - schedule_result（第 2 组排课时间地点，选课冲突检测与课表需要）
--      - v_exchange_credit_ready / v_exchange_student / ex_di_grade（第 15 组冻结接口桩）
--      - sys_param / sys_code_dict / sys_backup_log（业务⑥ 6.1/6.2 系统维护支撑表）
--   3. DBA 正式合库时，外部表/视图直接切换为第 1、2、4、13、15 组正式对象，不覆盖对方表。
-- =====================================================================

SET NAMES utf8mb4;

-- 1. 补充 student 的专业与学院字段（联调限选条件与专业排名用）
ALTER TABLE student
  ADD COLUMN major_id   CHAR(6) NULL COMMENT '专业编号（联调补充，待与第1组对齐）' AFTER class_name,
  ADD COLUMN college_id CHAR(4) NULL COMMENT '学院编号（联调补充，待与第1组对齐）' AFTER major_id;

-- 2. 补充 course 的课程培养层次（联调 sc.stat_stage 自动判定用）
ALTER TABLE course
  ADD COLUMN course_level TINYINT NULL DEFAULT 1 COMMENT '课程层次：1本科 2研究生（联调补充）' AFTER open_college;

-- 3. 第 2 组排课结果桩表（联调时间冲突检测、课表与合班建议用）
CREATE TABLE IF NOT EXISTS schedule_result (
  schedule_id INT         NOT NULL AUTO_INCREMENT COMMENT '排课记录号（联调桩主键）',
  tc_id       VARCHAR(20) NOT NULL COMMENT '教学班号，关联 teaching_class.tc_id',
  week_range  VARCHAR(30) NOT NULL COMMENT '上课周次，如 1-16 或 1-8,10-16',
  week_day    TINYINT     NOT NULL COMMENT '星期几：1周一..7周日',
  start_slot  TINYINT     NOT NULL COMMENT '开始节次：1..12',
  end_slot    TINYINT     NOT NULL COMMENT '结束节次：1..12',
  room_id     VARCHAR(20) NOT NULL COMMENT '教室编号，如 逸夫楼301',
  PRIMARY KEY (schedule_id),
  KEY idx_schedule_tc (tc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第2组排课结果（联调用，合库时替换）';

-- 4. 第 15 组外出交流返校资格桩表 & 只读视图 v_exchange_credit_ready（v8 已冻结接口口径）
CREATE TABLE IF NOT EXISTS stub_ex_credit_ready (
  application_no        VARCHAR(40)  NOT NULL COMMENT '外出交流申请业务号',
  student_id            CHAR(20)     NOT NULL COMMENT '本校学号',
  term_ref              CHAR(11)     NOT NULL COMMENT '交流归属学期',
  partner_school_snapshot VARCHAR(150) NOT NULL COMMENT '合作交流院校名称',
  ready_flag            TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已返校且备案可换算 0=未就绪',
  PRIMARY KEY (application_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第15组外出交流返校资格源表（联调用）';

CREATE OR REPLACE VIEW v_exchange_credit_ready AS
SELECT application_no, student_id, term_ref, partner_school_snapshot, ready_flag
FROM stub_ex_credit_ready;

-- 5. 第 15 组来校交换生身份桩表 & 只读视图 v_exchange_student（v8 已冻结接口口径）
CREATE TABLE IF NOT EXISTS stub_ex_incoming_student (
  temp_student_no    CHAR(20)     NOT NULL COMMENT '来校交换生临时学号（≤20位）',
  student_name       VARCHAR(50)  NOT NULL COMMENT '姓名',
  sender_school_name VARCHAR(150) NOT NULL COMMENT '派出院校名称',
  term_ref           CHAR(11)     NOT NULL COMMENT '来校学期',
  college_ref        CHAR(4)      NOT NULL COMMENT '接收学院编号',
  major_ref          CHAR(6)      NOT NULL COMMENT '接收专业编号',
  start_on           DATE         NOT NULL COMMENT '来校有效期开始',
  end_on             DATE         NOT NULL COMMENT '来校有效期结束',
  status_code        VARCHAR(20)  NOT NULL COMMENT '在读状态：ACTIVE/FINISHED',
  PRIMARY KEY (temp_student_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第15组来校交换生源表（联调用）';

CREATE OR REPLACE VIEW v_exchange_student AS
SELECT temp_student_no, student_name, sender_school_name, term_ref,
       college_ref, major_ref, start_on, end_on, status_code
FROM stub_ex_incoming_student;

-- 6. 第 15 组来校交换生成绩回执表桩（v8 已冻结接口口径）
CREATE TABLE IF NOT EXISTS ex_di_grade (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  temp_student_no CHAR(20)     NOT NULL,
  term_ref        CHAR(11)     NOT NULL,
  course_id       CHAR(8)      NOT NULL,
  course_name     VARCHAR(40)  NOT NULL,
  credit          DECIMAL(3,1) NOT NULL,
  total_score     DECIMAL(4,1) NULL,
  score_level     VARCHAR(6)   NULL,
  grade_point     DECIMAL(3,2) NULL,
  publish_time    DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ex_di_grade (temp_student_no, term_ref, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '桩表：第15组来校交换生成绩回执表（联调用）';

-- 7. 业务⑥ 系统维护（6.1 编码维护、6.2 数据备份与恢复）辅助表
CREATE TABLE IF NOT EXISTS sys_code_dict (
  dict_id    INT          NOT NULL AUTO_INCREMENT,
  dict_group VARCHAR(40)  NOT NULL COMMENT '编码分组，如 usual_ratio_min、term_current、college_map',
  dict_code  VARCHAR(40)  NOT NULL COMMENT '编码键',
  dict_name  VARCHAR(100) NOT NULL COMMENT '名称/取值',
  remark     VARCHAR(200) NULL,
  is_enabled TINYINT      NOT NULL DEFAULT 1,
  update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (dict_id),
  UNIQUE KEY uk_sys_code_dict (dict_group, dict_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数与编码维护表（业务⑥ 6.1）';

CREATE TABLE IF NOT EXISTS sys_backup_log (
  backup_id    BIGINT       NOT NULL AUTO_INCREMENT,
  backup_type  TINYINT      NOT NULL COMMENT '1手工备份 2恢复前应急快照',
  file_name    VARCHAR(150) NOT NULL COMMENT '备份文件名',
  file_size_kb INT          NULL,
  operator_id  VARCHAR(20)  NOT NULL,
  backup_time  DATETIME     NOT NULL,
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 2失败',
  remark       VARCHAR(200) NULL,
  PRIMARY KEY (backup_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据备份与恢复日志表（业务⑥ 6.2）';
