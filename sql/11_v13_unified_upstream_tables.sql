-- =====================================================================
-- 03 组 · 选课及成绩管理 —— 上游公共/外部表统一版建表脚本（DBA 统一字典全量对齐）
-- 文件：11_v13_unified_upstream_tables.sql
-- 依据：系统数据字典统一版v1.4.xlsx（第1/2/4/5/7/9/13/15组 + 第16组公共主数据）
-- 用途：
--   1. 在本组独立开发与联调库中，按全班统一数据字典的真实表名、字段名
--      与数据类型建立上游表与跨组视图，确保本组代码可直接平滑对接全班合库；
--   2. 全班正式合库时，由 DBA 直接使用各主维护组的正式表，不重复执行本脚本。
-- =====================================================================

SET NAMES utf8mb4;

-- =====================================================================
-- 一、DBA 公共主数据与统一字典表（4 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS org_organization (
  org_id        CHAR(6)      NOT NULL COMMENT '组织机构ID',
  org_code      VARCHAR(20)  NOT NULL COMMENT '机构编码',
  org_name      VARCHAR(100) NOT NULL COMMENT '机构名称',
  org_short     VARCHAR(30)  NULL     COMMENT '机构简称',
  org_type      CHAR(2)      NOT NULL COMMENT '机构类型',
  parent_org_id CHAR(6)      NULL     COMMENT '上级机构',
  college_id    CHAR(4)      NULL     COMMENT '学院业务编码（如 0001）',
  leader_emp_id CHAR(10)     NULL     COMMENT '负责人工号',
  contact_phone VARCHAR(20)  NULL     COMMENT '联系电话',
  org_status    CHAR(2)      NOT NULL DEFAULT '01' COMMENT '机构状态',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (org_id),
  UNIQUE KEY uk_org_organization_code (org_code),
  UNIQUE KEY uk_org_organization_college (college_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一组织机构表（v1.3）';

CREATE TABLE IF NOT EXISTS sys_person (
  person_id        CHAR(20)     NOT NULL COMMENT '自然人ID',
  person_no        VARCHAR(20)  NULL     COMMENT '学号或工号',
  person_name      VARCHAR(100) NOT NULL COMMENT '姓名',
  person_type      CHAR(2)      NOT NULL COMMENT '人员类别：01学生 02教职工 03交流生',
  gender           CHAR(2)      NOT NULL DEFAULT '9' COMMENT '性别：1男 2女 9未说明',
  id_card_no       CHAR(18)     NULL     COMMENT '身份证号',
  birth_date       DATE         NULL     COMMENT '出生日期',
  ethnicity        VARCHAR(30)  NULL     COMMENT '民族',
  political_status CHAR(2)      NULL     COMMENT '政治面貌',
  phone            VARCHAR(20)  NULL     COMMENT '联系电话',
  home_address     VARCHAR(255) NULL     COMMENT '家庭住址',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at       DATETIME     NULL     COMMENT '更新时间',
  PRIMARY KEY (person_id),
  UNIQUE KEY uk_sys_person_no (person_no),
  UNIQUE KEY uk_sys_person_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一自然人主索引（v1.3）';

CREATE TABLE IF NOT EXISTS al_dict_type (
  dict_type   VARCHAR(40)  NOT NULL COMMENT '字典类型码',
  type_name   VARCHAR(100) NOT NULL COMMENT '类型名称',
  is_system   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置：0否 1是',
  dict_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '类型状态：01启用 02停用',
  remark      VARCHAR(500) NULL     COMMENT '说明',
  PRIMARY KEY (dict_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一字典类型表（v1.3）';

CREATE TABLE IF NOT EXISTS al_dict_item (
  dict_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  dict_type      VARCHAR(40)  NOT NULL COMMENT '字典类型码，关联 al_dict_type.dict_type',
  item_code      VARCHAR(100) NOT NULL COMMENT '码值',
  item_name      VARCHAR(200) NOT NULL COMMENT '码值名称',
  sort_no        INT          NULL     DEFAULT 0 COMMENT '排序号',
  version        VARCHAR(40)  NOT NULL DEFAULT '2026' COMMENT '版本年/备注',
  effective_from DATE         NOT NULL DEFAULT '2026-01-01' COMMENT '生效日期',
  effective_to   DATE         NULL     COMMENT '失效日期',
  item_status    CHAR(2)      NOT NULL DEFAULT '01' COMMENT '字典项状态：01启用 02停用',
  PRIMARY KEY (dict_id),
  UNIQUE KEY uk_al_dict_item_type_code (dict_type, item_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'DBA 统一字典项表（v1.3）';

-- =====================================================================
-- 二、第 4 组 师资管理（1 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS hr_employee (
  emp_id         CHAR(10)      NOT NULL COMMENT '教工号',
  person_id      CHAR(20)      NOT NULL COMMENT '自然人ID，关联 sys_person.person_id',
  emp_name       VARCHAR(50)   NOT NULL COMMENT '姓名',
  id_card        VARCHAR(20)   NOT NULL COMMENT '身份证号',
  phone          VARCHAR(20)   NULL     COMMENT '联系电话',
  org_id         CHAR(6)       NOT NULL COMMENT '所属机构，关联 org_organization.org_id',
  post_type      VARCHAR(20)   NOT NULL DEFAULT 'TEACHER' COMMENT '岗位类别',
  post_level     VARCHAR(20)   NULL     COMMENT '岗位等级',
  title_code     CHAR(40)      NULL     COMMENT '职称代码',
  edu_level      VARCHAR(20)   NULL     COMMENT '最高学历',
  hire_date      DATE          NOT NULL DEFAULT '2020-09-01' COMMENT '入职日期',
  work_date      DATE          NULL     COMMENT '参加工作日期',
  emp_status     CHAR(2)       NOT NULL DEFAULT '01' COMMENT '在职状态',
  salary         DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '工资',
  monthly_salary DECIMAL(18,2) NULL     COMMENT '月工资标准',
  created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (emp_id),
  UNIQUE KEY uk_hr_employee_id_card (id_card),
  KEY idx_hr_employee_org (org_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第4组 教职工主数据（v1.3）';

-- =====================================================================
-- 三、第 2 组 专业设置及排课（7 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS aca_term (
  term          CHAR(11)   NOT NULL COMMENT '学年学期，如 2026-2027-1',
  academic_year CHAR(9)    NOT NULL COMMENT '学年，如 2026-2027',
  term_no       TINYINT(1) NOT NULL COMMENT '学期序号：1秋季 2春季',
  start_date    DATE       NOT NULL COMMENT '开始日期',
  end_date      DATE       NOT NULL COMMENT '结束日期',
  term_status   CHAR(2)    NOT NULL DEFAULT '01' COMMENT '学期状态',
  PRIMARY KEY (term)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 学期主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_major (
  major_id     CHAR(6)      NOT NULL COMMENT '专业编号',
  major_name   VARCHAR(100) NOT NULL COMMENT '专业名称',
  org_id       CHAR(6)      NOT NULL COMMENT '所属学院 org_id',
  college_id   CHAR(4)      NULL     COMMENT '学院业务编码',
  duration     INT          NOT NULL DEFAULT 4 COMMENT '学制',
  degree_name  VARCHAR(20)  NULL     COMMENT '授予学位',
  min_credit   DECIMAL(5,1) NOT NULL DEFAULT 160.0 COMMENT '毕业最低学分',
  major_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '专业状态',
  PRIMARY KEY (major_id),
  UNIQUE KEY uk_aca_major_name (major_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 专业主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_course (
  course_id     CHAR(10)      NOT NULL COMMENT '课程编号',
  course_code   VARCHAR(20)   NULL     COMMENT '课程显示码',
  course_name   VARCHAR(100)  NOT NULL COMMENT '课程名称',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质：11校级必修 12院级必修 13专业必修 14专业选修 15通识 16实践',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  total_hours   DECIMAL(18,2) NULL     COMMENT '总学时',
  org_id        CHAR(6)       NOT NULL COMMENT '开课学院 org_id',
  course_status CHAR(2)       NOT NULL DEFAULT '01' COMMENT '课程状态',
  PRIMARY KEY (course_id),
  UNIQUE KEY uk_aca_course_code (course_code),
  KEY idx_aca_course_org (org_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 课程主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_course_id (
  course_id     CHAR(10)      NOT NULL COMMENT '课程号，关联 aca_course.course_id',
  course_name   VARCHAR(100)  NOT NULL COMMENT '课程名称',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  total_hours   DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '总学时',
  assess_type   VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '考核方式：1考试 2考查',
  open_college  VARCHAR(20)   NOT NULL COMMENT '开课学院编码',
  pre_course_id VARCHAR(40)   NULL     COMMENT '先修课程',
  syllabus      VARCHAR(200)  NULL     COMMENT '课程大纲',
  course_status CHAR(2)       NOT NULL DEFAULT '1' COMMENT '课程状态',
  PRIMARY KEY (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 课程扩展表（v1.3，提供考核方式 assess_type）';

CREATE TABLE IF NOT EXISTS aca_teaching_class (
  tc_id             CHAR(20)      NOT NULL COMMENT '教学班编号',
  term              CHAR(11)      NOT NULL COMMENT '学年学期',
  course_id         CHAR(10)      NOT NULL COMMENT '课程编号',
  emp_id            CHAR(10)      NULL     COMMENT '任课教师工号，关联 hr_employee.emp_id',
  class_name        VARCHAR(50)   NULL     COMMENT '教学班名称',
  room_id           CHAR(12)      NULL     COMMENT '教室',
  capacity          INT           NOT NULL COMMENT '容量人数',
  selected_count    INT           NOT NULL DEFAULT 0 COMMENT '已选人数',
  actual_num        INT           NULL     DEFAULT 0 COMMENT '实际选课人数（第3组回写）',
  class_ids         VARCHAR(100)  NULL     COMMENT '面向班级',
  hours             DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '学时',
  multiclass_prefer VARCHAR(20)   NOT NULL DEFAULT '2' COMMENT '连排偏好',
  no_open_reason    VARCHAR(100)  NULL     COMMENT '不开课原因',
  open_lower        INT           NULL     DEFAULT 6 COMMENT '开课下限',
  prefer_slot       VARCHAR(20)   NULL     COMMENT '教师偏好节次',
  source            VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '数据来源',
  student_num       INT           NOT NULL DEFAULT 0 COMMENT '班级人数',
  tc_type           VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '教学班类型：1必修班 2选修班 3双学位班',
  teach_type        VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '教学形式',
  tc_status         CHAR(2)       NOT NULL DEFAULT '4' COMMENT '教学班状态：4已发布 2不开课 5取消',
  unavailable_time  VARCHAR(50)   NULL     COMMENT '不可用时间',
  week_count        INT           NOT NULL DEFAULT 16 COMMENT '上课周数',
  PRIMARY KEY (tc_id),
  KEY idx_aca_tc_term (term),
  KEY idx_aca_tc_course (course_id),
  KEY idx_aca_tc_emp (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 教学班主数据（v1.3）';

CREATE TABLE IF NOT EXISTS aca_program_course (
  major_id      CHAR(6)       NOT NULL COMMENT '专业编号',
  grade         VARCHAR(40)   NOT NULL COMMENT '适用年级',
  program_type  VARCHAR(20)   NOT NULL DEFAULT '1' COMMENT '方案类型：1主修 2双学位',
  course_id     CHAR(10)      NOT NULL COMMENT '课程号',
  course_type   CHAR(2)       NOT NULL COMMENT '课程性质',
  credit        DECIMAL(5,1)  NOT NULL COMMENT '学分',
  hours         DECIMAL(18,2) NOT NULL DEFAULT 48.00 COMMENT '学时',
  open_college  VARCHAR(20)   NOT NULL COMMENT '承担学院',
  open_semester INT           NOT NULL DEFAULT 1 COMMENT '开课学期',
  pre_course_id VARCHAR(40)   NULL     COMMENT '先修课程号',
  cross_status  CHAR(2)       NOT NULL DEFAULT '0' COMMENT '跨院确认状态',
  PRIMARY KEY (major_id, grade, program_type, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 培养方案课程表（v1.3）';

CREATE TABLE IF NOT EXISTS aca_schedule_result (
  sched_id      CHAR(14)     NOT NULL COMMENT '排课编号',
  tc_id         CHAR(20)     NOT NULL COMMENT '教学班编号',
  week_range    VARCHAR(20)  NOT NULL COMMENT '上课周次，如 1-16',
  week_day      VARCHAR(20)  NOT NULL COMMENT '星期：1周一..7周日',
  start_slot    INT          NOT NULL COMMENT '开始节次',
  end_slot      INT          NOT NULL COMMENT '结束节次',
  room_id       CHAR(12)     NOT NULL COMMENT '教室编号',
  lock_flag     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '锁定标志',
  sched_status  CHAR(2)      NOT NULL DEFAULT '0' COMMENT '排课状态',
  conflict_desc VARCHAR(200) NULL     COMMENT '冲突说明',
  PRIMARY KEY (sched_id),
  KEY idx_aca_sched_tc (tc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 排课结果表（v1.3）';

-- =====================================================================
-- 四、第 1 组 学籍管理（2 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS aca_class (
  class_id         CHAR(10)    NOT NULL COMMENT '班级编号',
  class_name       VARCHAR(50) NOT NULL COMMENT '班级名称',
  grade            VARCHAR(40) NOT NULL COMMENT '年级',
  major_id         CHAR(6)     NOT NULL COMMENT '专业编号',
  org_id           CHAR(6)     NULL     COMMENT '所属学院 org_id',
  counselor_emp_id CHAR(10)    NULL     COMMENT '辅导员工号',
  PRIMARY KEY (class_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 行政班主数据（v1.3）';

CREATE TABLE IF NOT EXISTS std_student (
  student_id       CHAR(20)    NOT NULL COMMENT '学号',
  person_id        CHAR(20)    NOT NULL COMMENT '自然人ID，关联 sys_person.person_id',
  class_id         CHAR(10)    NULL     COMMENT '行政班，关联 aca_class.class_id',
  major_id         CHAR(6)     NULL     COMMENT '专业编号，关联 aca_major.major_id',
  org_id           CHAR(6)     NOT NULL COMMENT '所属学院，关联 org_organization.org_id',
  grade            VARCHAR(40) NULL     COMMENT '年级（如 2024）',
  enrollment_year  INT         NOT NULL COMMENT '入学年份',
  enrollment_date  DATE        NULL     COMMENT '入学日期',
  graduation_date  DATE        NULL     COMMENT '毕业日期',
  student_type     CHAR(2)     NOT NULL COMMENT '学生类别：01本科 02硕士 03博士 04交流生',
  student_status   CHAR(2)     NOT NULL DEFAULT '01' COMMENT '学籍状态：01在籍',
  counselor_emp_id CHAR(10)    NULL     COMMENT '辅导员工号',
  dorm_id          CHAR(12)    NULL     COMMENT '宿舍',
  created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (student_id),
  KEY idx_std_student_person (person_id),
  KEY idx_std_student_major_grade (major_id, grade)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 学生主数据（v1.3）';

-- =====================================================================
-- 五、第 13 组 研究生管理（3 表）
-- =====================================================================

CREATE TABLE IF NOT EXISTS grad_direction (
  direction_id   VARCHAR(20) NOT NULL COMMENT '研究方向编号',
  direction_name VARCHAR(50) NOT NULL COMMENT '研究方向名称',
  PRIMARY KEY (direction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究方向表（v1.3）';

CREATE TABLE IF NOT EXISTS grad_scheme_assignment (
  assignment_id VARCHAR(50) NOT NULL COMMENT '指定标识',
  student_id    CHAR(20)    NOT NULL COMMENT '学生标识',
  scheme_id     VARCHAR(50) NOT NULL COMMENT '方案版本标识',
  direction_id  VARCHAR(20) NULL     COMMENT '研究方向编号',
  method        VARCHAR(50) NOT NULL DEFAULT 'DEFAULT' COMMENT '指定方式',
  valid_from    DATE        NOT NULL COMMENT '开始日期（原 effective_from）',
  valid_to      DATE        NULL     COMMENT '结束日期（原 effective_to）',
  PRIMARY KEY (assignment_id),
  KEY idx_grad_assign_student (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生方案指定表（v1.3）';

CREATE TABLE IF NOT EXISTS grad_scheme_course (
  scheme_course_id VARCHAR(50) NOT NULL COMMENT '方案课程标识',
  scheme_id        VARCHAR(50) NOT NULL COMMENT '方案标识',
  course_id        CHAR(10)    NOT NULL COMMENT '课程标识',
  direction_id     VARCHAR(20) NULL     COMMENT '研究方向编号',
  group_code       VARCHAR(50) NOT NULL DEFAULT 'CORE' COMMENT '课程分类代码',
  course_category  INT         NOT NULL COMMENT '课程类别：1年度必修 2方向必修 3方向选修',
  suggest_term     INT         NULL     COMMENT '建议修读学期',
  PRIMARY KEY (scheme_course_id),
  KEY idx_grad_scheme_course (scheme_id, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生方案课程表（v1.3）';

-- =====================================================================
-- 六、第 15 组 对外交流管理（6 表 + 2 跨组冻结只读视图）
-- =====================================================================

CREATE TABLE IF NOT EXISTS exg_application (
  apply_id           VARCHAR(40)  NOT NULL COMMENT '申请编号',
  person_id          CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name       VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  id_card_no         CHAR(18)     NOT NULL COMMENT '身份证号',
  home_school        VARCHAR(100) NOT NULL COMMENT '派出学校',
  home_student_no    VARCHAR(20)  NOT NULL COMMENT '派出校学号',
  apply_college_name VARCHAR(100) NOT NULL COMMENT '申请学院',
  apply_major_name   VARCHAR(100) NOT NULL COMMENT '申请专业',
  apply_term         VARCHAR(20)  NOT NULL COMMENT '申请学期',
  contact_email      VARCHAR(100) NULL     COMMENT '联系邮箱',
  apply_status       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '申请状态',
  submitted_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后修改时间',
  PRIMARY KEY (apply_id),
  UNIQUE KEY uk_exg_app_home_no (home_student_no),
  UNIQUE KEY uk_exg_app_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生申请表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_admission (
  admission_id     CHAR(40)     NOT NULL COMMENT '录取编号',
  apply_id         VARCHAR(40)  NOT NULL COMMENT '关联申请编号',
  person_id        CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name     VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  college_name     VARCHAR(100) NOT NULL COMMENT '录取学院',
  major_name       VARCHAR(100) NOT NULL COMMENT '录取专业',
  term             CHAR(11)     NOT NULL COMMENT '交流学期',
  interview_score  DECIMAL(5,2) NOT NULL DEFAULT 90.00 COMMENT '面试成绩',
  approver_name    VARCHAR(50)  NOT NULL DEFAULT '教务处' COMMENT '审批人',
  admission_time   DATE         NOT NULL COMMENT '录取时间',
  admission_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '录取状态',
  PRIMARY KEY (admission_id),
  UNIQUE KEY uk_exg_adm_apply (apply_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生录取表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_temp_student (
  temp_student_id CHAR(20)     NOT NULL COMMENT '临时学号',
  admission_id    CHAR(40)     NOT NULL COMMENT '关联录取编号',
  person_id       CHAR(20)     NOT NULL COMMENT '自然人ID',
  student_name    VARCHAR(50)  NOT NULL COMMENT '学生姓名',
  id_card_no      CHAR(18)     NOT NULL COMMENT '身份证号',
  org_id          CHAR(6)      NOT NULL COMMENT '接收学院 org_id',
  college_name    VARCHAR(100) NOT NULL COMMENT '所属学院',
  major_name      VARCHAR(100) NOT NULL COMMENT '所学专业',
  start_date      DATE         NOT NULL COMMENT '交流开始日期',
  end_date        DATE         NOT NULL COMMENT '交流结束日期',
  student_status  CHAR(2)      NOT NULL DEFAULT '01' COMMENT '学籍状态：01在读 02已结业',
  creator_name    VARCHAR(50)  NOT NULL DEFAULT '国际处' COMMENT '创建人',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (temp_student_id),
  UNIQUE KEY uk_exg_temp_adm (admission_id),
  UNIQUE KEY uk_exg_temp_id_card (id_card_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生临时学籍表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_departure (
  departure_id        CHAR(20)     NOT NULL COMMENT '出境登记编号',
  apply_id            VARCHAR(40)  NOT NULL COMMENT '申请编号',
  student_id          CHAR(20)     NOT NULL COMMENT '学号',
  host_country        VARCHAR(50)  NOT NULL COMMENT '目的地国家或地区',
  partner_school      VARCHAR(100) NOT NULL COMMENT '合作院校',
  departure_date      DATE         NOT NULL COMMENT '出境日期',
  planned_return_date DATE         NOT NULL COMMENT '计划返校日期',
  actual_return_date  DATE         NULL     COMMENT '实际返校日期',
  return_status       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '返校状态',
  PRIMARY KEY (departure_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流出境登记表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_return_report (
  report_id               VARCHAR(50)  NOT NULL COMMENT '报到编号',
  departure_id            CHAR(20)     NOT NULL COMMENT '出境登记编号',
  student_id              CHAR(20)     NOT NULL COMMENT '学号',
  actual_return_date      DATE         NOT NULL COMMENT '实际返校日期',
  report_date             DATE         NOT NULL COMMENT '报到日期',
  return_apply_file       VARCHAR(255) NOT NULL DEFAULT 'return_apply.pdf' COMMENT '返校报到申请表',
  partner_transcript_file DECIMAL(5,2) NULL     COMMENT '合作院校成绩单标记',
  report_note             VARCHAR(500) NULL     COMMENT '报到说明',
  PRIMARY KEY (report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流返校报到表（v1.3）';

CREATE TABLE IF NOT EXISTS exg_vst_return_filing (
  return_filing_id   CHAR(20)    NOT NULL COMMENT '返校备案编号',
  report_id          VARCHAR(50) NOT NULL COMMENT '报到编号',
  student_id         CHAR(20)    NOT NULL COMMENT '学号',
  actual_return_date DATE        NOT NULL COMMENT '实际返校日期',
  filing_date        DATE        NOT NULL COMMENT '备案日期',
  filer              VARCHAR(50) NOT NULL DEFAULT '国际处' COMMENT '备案人',
  filing_status      CHAR(2)     NOT NULL DEFAULT '01' COMMENT '备案状态：01已备案 00待备案',
  PRIMARY KEY (return_filing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外出交流返校备案表（v1.3）';

-- 跨组冻结只读接口视图（基于第 15 组 v1.3 正式表投影，保持第 3 组与第 15 组冻结视图口径）
CREATE OR REPLACE VIEW v_exchange_student AS
SELECT ts.temp_student_id AS temp_student_no,
       ts.student_name,
       app.home_school AS sender_school_name,
       adm.term AS term_ref,
       COALESCE(org.college_id, '0001') AS college_ref,
       COALESCE(m.major_id, '120102') AS major_ref,
       ts.start_date AS start_on,
       ts.end_date AS end_on,
       CASE WHEN ts.student_status IN ('01', '1') THEN 'ACTIVE' ELSE 'FINISHED' END AS status_code
FROM exg_temp_student ts
JOIN exg_admission adm ON ts.admission_id = adm.admission_id
JOIN exg_application app ON adm.apply_id = app.apply_id
LEFT JOIN org_organization org ON ts.org_id = org.org_id
LEFT JOIN aca_major m ON ts.major_name = m.major_name;

-- 跨组冻结只读接口视图 v_exchange_credit_ready 定义移至第八节全部上游表建表之后

-- =====================================================================
-- 八、全班跨组深化接入上游表（第1/2/4/5/7/9/13/15组，对齐统一数据字典）
-- =====================================================================

-- 8.1 第 1 组 学籍管理扩展（新学期注册、辅修/双学位资格、转专业记录）
CREATE TABLE IF NOT EXISTS std_registration (
  student_id          CHAR(20)     NOT NULL COMMENT '学号',
  term                CHAR(11)     NOT NULL COMMENT '学期标识',
  student_name        VARCHAR(50)  NOT NULL COMMENT '姓名',
  college_name        VARCHAR(100) NOT NULL COMMENT '学院',
  org_id              CHAR(6)      NOT NULL COMMENT '关联机构ID',
  class_name          VARCHAR(50)  NOT NULL COMMENT '班级',
  enrollment_status   CHAR(2)      NOT NULL DEFAULT '01' COMMENT '当前学籍状态',
  payment_status      CHAR(2)      NOT NULL DEFAULT '01' COMMENT '缴学费状态',
  info_confirm        VARCHAR(20)  NOT NULL DEFAULT '已确认' COMMENT '信息确认状态',
  registration_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '注册状态：01已注册 02未注册/异常',
  registration_time   DATE         NOT NULL COMMENT '注册时间',
  approver_id         VARCHAR(40)  NULL     COMMENT '审批人工号',
  abnormal_reason     VARCHAR(255) NULL     COMMENT '异常情况说明',
  PRIMARY KEY (student_id, term),
  KEY idx_std_reg_term (term)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 新学期学籍注册表';

CREATE TABLE IF NOT EXISTS std_minor_degree (
  apply_id             VARCHAR(40)  NOT NULL COMMENT '申请编号',
  person_id            CHAR(20)     NOT NULL COMMENT '自然人ID，关联 sys_person.person_id',
  student_name         VARCHAR(50)  NOT NULL COMMENT '姓名',
  apply_category       VARCHAR(20)  NOT NULL COMMENT '申请类型：辅修/双学位',
  target_major         VARCHAR(100) NOT NULL COMMENT '目标专业',
  grade_qualification  VARCHAR(20)  NOT NULL COMMENT '年级资格结论',
  score_qualification  DECIMAL(5,2) NOT NULL COMMENT '成绩资格结论',
  minor_review_result  VARCHAR(20)  NOT NULL COMMENT '初审结果',
  minor_review_opinion VARCHAR(500) NULL     COMMENT '初审意见',
  college_result       VARCHAR(20)  NOT NULL COMMENT '学院审核结果',
  college_opinion      VARCHAR(500) NULL     COMMENT '学院审核意见',
  final_result         VARCHAR(20)  NOT NULL COMMENT '终审结果',
  final_opinion        VARCHAR(500) NULL     COMMENT '终审意见',
  apply_status         CHAR(2)      NOT NULL DEFAULT '01' COMMENT '申请状态：03终审通过',
  submitted_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (apply_id),
  KEY idx_std_minor_person (person_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 辅修与双学位资格表';

CREATE TABLE IF NOT EXISTS std_major_transfer (
  student_id                   CHAR(20)     NOT NULL COMMENT '学号',
  application_term             VARCHAR(20)  NOT NULL COMMENT '申请学期',
  current_major_id             VARCHAR(20)  NOT NULL COMMENT '当前专业代码',
  target_major_id              VARCHAR(20)  NOT NULL COMMENT '申请转入专业代码',
  weighted_average_score       DECIMAL(5,2) NULL     COMMENT '加权成绩',
  current_major_rank           INT          NULL     COMMENT '目前专业排名',
  current_major_rank_total     INT          NULL     COMMENT '排名总人数',
  target_major_quota           INT          NULL     COMMENT '转入专业计划接收人数',
  target_major_applicant_count INT          NULL     COMMENT '转入专业申请人数',
  application_status           CHAR(2)      NOT NULL DEFAULT '01' COMMENT '申请状态',
  admission_result             VARCHAR(20)  NULL     COMMENT '录取情况',
  submitted_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请日期',
  decision_date                DATE         NULL     COMMENT '审批日期',
  effective_term               VARCHAR(20)  NULL     COMMENT '转入生效学期',
  student_record_updated       TINYINT(1)   NULL     DEFAULT 0 COMMENT '学籍信息已更新',
  PRIMARY KEY (student_id, application_term)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第1组 转专业申请与结果表';

-- 8.2 第 2 组 专业设置及排课扩展（调课记录、培养方案总纲、通识计划、学分转换、排课冲突、双学位录取）
CREATE TABLE IF NOT EXISTS aca_adjust_record (
  adjust_id      CHAR(12)     NOT NULL COMMENT '调课编号',
  sched_id       CHAR(14)     NOT NULL COMMENT '排课编号',
  adjust_type    VARCHAR(20)  NOT NULL COMMENT '调课类型',
  old_value      VARCHAR(100) NOT NULL COMMENT '原安排',
  new_value      VARCHAR(100) NOT NULL COMMENT '新安排',
  reason         VARCHAR(500) NOT NULL COMMENT '调课原因',
  approve_status CHAR(2)      NOT NULL DEFAULT '0' COMMENT '审批状态：1已批准',
  notify_status  CHAR(2)      NOT NULL DEFAULT '0' COMMENT '通知状态',
  restore_flag   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '恢复标志',
  operated_at    DATE         NOT NULL COMMENT '操作时间',
  valid_from     DATE         NULL     COMMENT '生效日期',
  approver       VARCHAR(40)  NULL     COMMENT '审批人',
  approve_opinion VARCHAR(200) NULL    COMMENT '审批意见',
  old_week_range VARCHAR(20)  NULL     COMMENT '原周次范围',
  old_week_day   VARCHAR(20)  NULL     COMMENT '原星期',
  old_start_slot INT          NULL     COMMENT '原开始节次',
  old_end_slot   INT          NULL     COMMENT '原结束节次',
  old_room_id    VARCHAR(20)  NULL     COMMENT '原教室',
  new_week_day   VARCHAR(20)  NULL     COMMENT '新星期',
  new_start_slot INT          NULL     COMMENT '新开始节次',
  new_end_slot   INT          NULL     COMMENT '新结束节次',
  new_room_id    VARCHAR(20)  NULL     COMMENT '新教室',
  PRIMARY KEY (adjust_id),
  KEY idx_aca_adjust_sched (sched_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 调停课记录表';

CREATE TABLE IF NOT EXISTS aca_major_program (
  major_id       CHAR(6)      NOT NULL COMMENT '专业编号',
  grade          VARCHAR(40)  NOT NULL COMMENT '适用年级',
  program_type   VARCHAR(20)  NOT NULL DEFAULT '1' COMMENT '方案类型：1主修 2双学位',
  version        VARCHAR(40)  NOT NULL COMMENT '方案版次',
  total_credit   DECIMAL(5,1) NOT NULL COMMENT '毕业总学分',
  credit_rule    VARCHAR(200) NULL     COMMENT '分类学分要求（性质码:最低学分;…）',
  goal_desc      VARCHAR(500) NOT NULL COMMENT '培养目标',
  plan_status    CHAR(2)      NOT NULL DEFAULT '0' COMMENT '方案状态',
  dual_quota     INT          NULL     COMMENT '双学位名额',
  published_at   DATE         NULL     COMMENT '发布日期',
  review_opinion VARCHAR(500) NULL     COMMENT '审核/审议意见',
  PRIMARY KEY (major_id, grade, program_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 专业培养方案总表';

CREATE TABLE IF NOT EXISTS aca_general_plan (
  school_year       VARCHAR(20) NOT NULL COMMENT '学年',
  course_id         CHAR(10)    NOT NULL COMMENT '课程号',
  plan_category     VARCHAR(20) NOT NULL COMMENT '通识类别',
  suggest_hours     INT         NOT NULL COMMENT '建议学时',
  undertake_college VARCHAR(20) NULL     COMMENT '承接学院',
  emp_id            CHAR(10)    NULL     COMMENT '拟派教师',
  confirm_status    CHAR(2)     NOT NULL DEFAULT '0' COMMENT '承接状态',
  plan_status       CHAR(2)     NOT NULL DEFAULT '0' COMMENT '计划状态',
  PRIMARY KEY (school_year, course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 通识教育课程计划表';

CREATE TABLE IF NOT EXISTS aca_credit_apply (
  apply_id        VARCHAR(40)  NOT NULL COMMENT '申请编号',
  student_id      CHAR(20)     NOT NULL COMMENT '学号',
  from_major      VARCHAR(20)  NOT NULL COMMENT '原专业',
  to_major        VARCHAR(20)  NOT NULL COMMENT '转入专业',
  apply_credit    DECIMAL(5,1) NULL     COMMENT '申请转换学分',
  apply_date      DATE         NOT NULL COMMENT '申请日期',
  audit_status    CHAR(2)      NOT NULL DEFAULT '0' COMMENT '审核状态',
  college_opinion VARCHAR(500) NULL     COMMENT '学院审核意见',
  approver        VARCHAR(40)  NULL     COMMENT '审核人',
  audit_date      DATE         NULL     COMMENT '审核日期',
  audit_opinion   VARCHAR(500) NULL     COMMENT '审核意见',
  PRIMARY KEY (apply_id),
  KEY idx_aca_credit_apply_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 转专业学分转换申请表';

CREATE TABLE IF NOT EXISTS aca_credit_item (
  apply_id         VARCHAR(40)  NOT NULL COMMENT '申请编号',
  seq_no           INT          NOT NULL COMMENT '明细序号',
  source_course_id VARCHAR(20)  NOT NULL COMMENT '原修课程',
  source_credit    DECIMAL(5,1) NOT NULL COMMENT '原课程学分',
  target_course_id VARCHAR(20)  NULL     COMMENT '认定课程',
  target_credit    DECIMAL(5,1) NULL     COMMENT '认定学分',
  transfer_type    VARCHAR(10)  NOT NULL DEFAULT '01' COMMENT '转换类型',
  item_status      CHAR(2)      NOT NULL DEFAULT '0' COMMENT '明细状态',
  PRIMARY KEY (apply_id, seq_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 学分转换明细表';

CREATE TABLE IF NOT EXISTS aca_schedule_conflict (
  conflict_id   VARCHAR(40)  NOT NULL COMMENT '冲突编号',
  tc_id         CHAR(20)     NOT NULL COMMENT '教学班编号',
  other_tc_id   VARCHAR(20)  NULL     COMMENT '对方教学班',
  conflict_type VARCHAR(20)  NOT NULL COMMENT '冲突类型',
  conflict_desc VARCHAR(200) NULL     COMMENT '冲突描述',
  suggest_plan  VARCHAR(200) NULL     COMMENT '建议方案',
  found_time    DATE         NOT NULL COMMENT '发现日期',
  handle_status CHAR(2)      NOT NULL DEFAULT '0' COMMENT '处理状态：0未处理 1已处理',
  PRIMARY KEY (conflict_id),
  KEY idx_aca_conflict_tc (tc_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 排课冲突记录表';

CREATE TABLE IF NOT EXISTS aca_dual_enroll (
  enroll_id     CHAR(12)     NOT NULL COMMENT '报名编号',
  student_id    CHAR(20)     NOT NULL COMMENT '学号',
  major_id      CHAR(6)      NOT NULL COMMENT '双学位专业',
  enroll_year   VARCHAR(20)  NOT NULL COMMENT '报名学年',
  apply_date    DATE         NULL     COMMENT '报名日期',
  audit_status  CHAR(2)      NOT NULL DEFAULT '0' COMMENT '录取状态：1已录取',
  enroll_date   DATE         NULL     COMMENT '录取日期',
  finish_status CHAR(2)      NOT NULL DEFAULT '0' COMMENT '修读状态',
  remark        VARCHAR(500) NULL     COMMENT '备注',
  PRIMARY KEY (enroll_id),
  KEY idx_aca_dual_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第2组 双学位报名录取表';

-- 8.3 第 4 组 师资管理扩展（教师请假、教职工出国）
CREATE TABLE IF NOT EXISTS hr_leave (
  leave_id      CHAR(40)      NOT NULL COMMENT '请假单号',
  emp_id        CHAR(10)      NOT NULL COMMENT '工号',
  leave_type    VARCHAR(20)   NOT NULL COMMENT '假别',
  start_at      VARCHAR(40)   NOT NULL COMMENT '开始时间',
  end_at        VARCHAR(40)   NOT NULL COMMENT '结束时间',
  days          DECIMAL(18,2) NOT NULL COMMENT '请假天数',
  reason        VARCHAR(500)  NOT NULL COMMENT '请假事由',
  approve_level VARCHAR(20)   NOT NULL COMMENT '审批层级',
  leave_status  CHAR(2)       NOT NULL COMMENT '单据状态：02已批准',
  PRIMARY KEY (leave_id),
  KEY idx_hr_leave_emp (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第4组 教职工请假记录表';

CREATE TABLE IF NOT EXISTS hr_abroad (
  abroad_id    CHAR(16)    NOT NULL COMMENT '申请单号',
  emp_id       CHAR(10)    NOT NULL COMMENT '工号',
  abroad_type  VARCHAR(20) NOT NULL COMMENT '出国类型',
  country      VARCHAR(30) NOT NULL COMMENT '国家（地区）',
  start_date   DATE        NOT NULL COMMENT '出国日期',
  end_date     DATE        NOT NULL COMMENT '回国日期',
  approve_doc  VARCHAR(30) NULL     COMMENT '批件文号',
  apply_status CHAR(2)     NOT NULL COMMENT '审批状态：02已批准',
  PRIMARY KEY (abroad_id),
  KEY idx_hr_abroad_emp (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第4组 教职工出国（境）记录表';

-- 8.4 第 5 组 财务管理（收费项目、学生缴费与欠费台账）
CREATE TABLE IF NOT EXISTS fin_charge_item (
  charge_item_id     CHAR(10)      NOT NULL COMMENT '收费项目ID',
  charge_item_name   VARCHAR(50)   NOT NULL COMMENT '收费项目名称',
  charge_type        CHAR(2)       NOT NULL COMMENT '收费类型',
  standard_amount    DECIMAL(18,2) NULL     COMMENT '标准金额',
  charge_item_status CHAR(2)       NOT NULL DEFAULT '01' COMMENT '项目状态',
  PRIMARY KEY (charge_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第5组 学生收费项目表';

CREATE TABLE IF NOT EXISTS fin_charge (
  charge_no         VARCHAR(40)   NOT NULL COMMENT '收费单号',
  student_id        CHAR(20)      NOT NULL COMMENT '学号',
  charge_item       VARCHAR(40)   NOT NULL COMMENT '收费项目编码',
  charge_period     VARCHAR(20)   NOT NULL COMMENT '所属期间',
  receivable_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '应收金额',
  received_amount   DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '已收金额',
  arrears_amount    DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '欠费金额',
  pay_method        VARCHAR(20)   NOT NULL DEFAULT '线上缴费' COMMENT '缴费方式',
  invoice_no        VARCHAR(40)   NULL     COMMENT '票据号',
  income_no         VARCHAR(30)   NULL     COMMENT '关联收入记录',
  audit_status      CHAR(2)       NOT NULL DEFAULT '01' COMMENT '审核状态',
  PRIMARY KEY (charge_no),
  KEY idx_fin_charge_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第5组 学生缴费与欠费台账表';

-- 8.5 第 7 组 固定资产管理（教学楼栋、教室与实验场地资源）
CREATE TABLE IF NOT EXISTS ast_building (
  building_id     CHAR(8)      NOT NULL COMMENT '楼栋ID',
  building_name   VARCHAR(100) NOT NULL COMMENT '楼栋名称',
  building_type   CHAR(2)      NULL     COMMENT '楼栋类型',
  campus_id       CHAR(6)      NULL     COMMENT '校区ID',
  floors          INT          NULL     COMMENT '楼层数',
  org_id          CHAR(6)      NULL     COMMENT '归口部门',
  address         VARCHAR(200) NULL     COMMENT '地址',
  building_status CHAR(2)      NOT NULL DEFAULT '1' COMMENT '状态',
  PRIMARY KEY (building_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第7组 教学与实验楼栋表';

CREATE TABLE IF NOT EXISTS aca_room (
  room_id         CHAR(12)     NOT NULL COMMENT '场地ID',
  room_code       VARCHAR(20)  NOT NULL COMMENT '场地编码',
  room_name       VARCHAR(20)  NOT NULL COMMENT '场地名称',
  building_id     CHAR(8)      NULL     COMMENT '楼栋ID',
  floor           INT          NULL     COMMENT '楼层',
  room_number     VARCHAR(20)  NULL     COMMENT '房间号',
  room_type       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '场地类型：01教室 02实验室',
  room_subtype    CHAR(4)      NULL     COMMENT '场地子类型',
  lab_category    CHAR(4)      NULL     COMMENT '实验室专业类别',
  parent_room_id  CHAR(12)     NULL     COMMENT '所属办公室ID',
  capacity        INT          NULL     COMMENT '容量人数',
  has_projector   CHAR(1)      NULL     DEFAULT '0' COMMENT '是否有投影：1有 0无',
  computer_num    INT          NULL     COMMENT '机位数',
  blackboard_type CHAR(2)      NULL     COMMENT '黑板类型',
  equip_desc      VARCHAR(200) NULL     COMMENT '主要设备说明',
  org_id          CHAR(6)      NULL     COMMENT '归口部门',
  room_status     CHAR(2)      NOT NULL DEFAULT '01' COMMENT '场地状态',
  PRIMARY KEY (room_id),
  UNIQUE KEY uk_aca_room_code (room_code),
  KEY idx_aca_room_building (building_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第7组 教学场地与教室资产表';

-- 8.6 第 9 组 实验室管理（实验室台账、临时封闭通知、教学预约）
CREATE TABLE IF NOT EXISTS lab_laboratory (
  lab_id         CHAR(12)     NOT NULL COMMENT '实验室ID',
  lab_name       VARCHAR(100) NOT NULL COMMENT '实验室名称',
  room_id        CHAR(12)     NOT NULL COMMENT '场地ID，关联 aca_room.room_id',
  org_id         CHAR(6)      NULL     COMMENT '归口学院',
  manager_emp_id CHAR(10)     NULL     COMMENT '负责人工号',
  capacity       INT          NULL     COMMENT '容量人数',
  open_time      VARCHAR(20)  NULL     COMMENT '开放时间',
  close_time     VARCHAR(40)  NULL     COMMENT '关闭时间',
  lab_status     CHAR(2)      NOT NULL DEFAULT '01' COMMENT '实验室状态',
  description    TEXT         NULL     COMMENT '描述',
  building       VARCHAR(100) NOT NULL COMMENT '楼栋名称',
  floor          INT          NOT NULL DEFAULT 1 COMMENT '楼层',
  room_number    VARCHAR(20)  NOT NULL COMMENT '房间号',
  location       VARCHAR(200) NOT NULL COMMENT '位置说明',
  lab_type       VARCHAR(20)  NOT NULL DEFAULT 'shared' COMMENT '场地用途',
  manager_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '负责人用户ID',
  image          VARCHAR(255) NULL     COMMENT '图片地址',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (lab_id),
  KEY idx_lab_room (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第9组 实验室主数据表';

CREATE TABLE IF NOT EXISTS lab_closure (
  closure_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '封闭记录ID',
  lab_id         CHAR(12)     NOT NULL COMMENT '实验室ID',
  start_at       VARCHAR(40)  NOT NULL COMMENT '封闭开始时间',
  end_at         VARCHAR(40)  NOT NULL COMMENT '封闭结束时间',
  reason         VARCHAR(500) NOT NULL COMMENT '封闭原因',
  closure_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '状态：01生效 00取消',
  created_by     VARCHAR(40)  NOT NULL COMMENT '创建人',
  cancelled_by   BIGINT       NULL     COMMENT '取消人',
  cancelled_at   DATE         NULL     COMMENT '取消时间',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (closure_id),
  KEY idx_lab_closure_lab (lab_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第9组 实验室临时封闭通知表';

CREATE TABLE IF NOT EXISTS lab_reservation (
  reservation_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '预约ID',
  lab_id             CHAR(12)     NOT NULL COMMENT '实验室ID',
  equipment_id       VARCHAR(50)  NULL     COMMENT '设备ID',
  user_id            BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID',
  date               DATE         NOT NULL COMMENT '使用日期',
  start_at           VARCHAR(40)  NOT NULL COMMENT '开始时间',
  end_at             VARCHAR(40)  NOT NULL COMMENT '结束时间',
  purpose            VARCHAR(500) NOT NULL COMMENT '用途',
  participants       INT          NOT NULL DEFAULT 1 COMMENT '参与人数',
  reservation_status CHAR(2)      NOT NULL DEFAULT '01' COMMENT '预约状态',
  source_type        VARCHAR(40)  NOT NULL DEFAULT 'teaching' COMMENT '预约来源',
  course_id          CHAR(10)     NOT NULL COMMENT '课程ID',
  course_name        VARCHAR(100) NULL     COMMENT '课程名称',
  course_teacher     VARCHAR(50)  NULL     COMMENT '任课教师姓名',
  week_no            INT          NULL     COMMENT '教学周次',
  active_slot_lock   INT          NULL     COMMENT '活跃时段锁',
  reviewer_id        VARCHAR(50)  NULL     COMMENT '审核人ID',
  review_comment     VARCHAR(500) NULL     COMMENT '审核意见',
  remark             VARCHAR(500) NULL     COMMENT '补充说明',
  created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (reservation_id),
  UNIQUE KEY uk_lab_res_slot (lab_id, date, start_at, end_at),
  KEY idx_lab_res_course (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第9组 实验室教学预约表';

-- 8.7 第 13 组 研究生管理扩展（培养方案主表、分类要求、个人培养计划、导师关系）
CREATE TABLE IF NOT EXISTS grad_training_scheme (
  scheme_id      VARCHAR(50)  NOT NULL COMMENT '方案版本标识',
  major_id       CHAR(6)      NOT NULL COMMENT '专业标识',
  grade          VARCHAR(40)  NOT NULL COMMENT '适用年级',
  level          VARCHAR(50)  NOT NULL COMMENT '适用层次类别',
  college_id     CHAR(4)      NOT NULL COMMENT '适用学院',
  version_no     VARCHAR(40)  NOT NULL COMMENT '方案版次',
  min_credit     DECIMAL(6,2) NOT NULL COMMENT '最低总学分',
  max_credit     DECIMAL(6,2) NULL     COMMENT '最高总学分',
  publish_status VARCHAR(40)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '发布状态',
  application_id VARCHAR(50)  NULL     COMMENT '业务申请标识',
  PRIMARY KEY (scheme_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生培养方案主表';

CREATE TABLE IF NOT EXISTS grad_scheme_requirement (
  scheme_line_id VARCHAR(50)  NOT NULL COMMENT '要求明细标识',
  scheme_id      VARCHAR(50)  NOT NULL COMMENT '方案标识',
  group_code     VARCHAR(50)  NOT NULL COMMENT '课程分类代码',
  min_credit     DECIMAL(6,2) NULL     COMMENT '分类最低学分',
  max_credit     DECIMAL(6,2) NULL     COMMENT '分类最高学分',
  min_count      INT          NULL     COMMENT '最低门数',
  PRIMARY KEY (scheme_line_id),
  KEY idx_grad_req_scheme (scheme_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生培养方案分类学分要求表';

CREATE TABLE IF NOT EXISTS grad_training_plan (
  plan_id        VARCHAR(50) NOT NULL COMMENT '计划标识',
  student_id     CHAR(20)    NOT NULL COMMENT '学生标识',
  scheme_id      VARCHAR(50) NOT NULL COMMENT '依据方案版本',
  version_no     INT         NOT NULL DEFAULT 1 COMMENT '计划版本',
  plan_status    VARCHAR(40) NOT NULL DEFAULT 'APPROVED' COMMENT '计划状态',
  application_id VARCHAR(50) NOT NULL COMMENT '业务申请标识',
  PRIMARY KEY (plan_id),
  KEY idx_grad_plan_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生个人培养计划表';

CREATE TABLE IF NOT EXISTS grad_plan_course (
  plan_line_id    VARCHAR(50)  NOT NULL COMMENT '计划课程标识',
  plan_id         VARCHAR(50)  NOT NULL COMMENT '计划标识',
  course_id       CHAR(10)     NOT NULL COMMENT '课程标识',
  course_source   VARCHAR(50)  NOT NULL DEFAULT 'SCHEME' COMMENT '课程来源',
  planned_term    VARCHAR(20)  NULL     COMMENT '拟修学期',
  credit_snapshot DECIMAL(6,2) NOT NULL COMMENT '计划学分快照',
  PRIMARY KEY (plan_line_id),
  KEY idx_grad_plan_course_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生个人培养计划课程表';

CREATE TABLE IF NOT EXISTS grad_student_mentor (
  student_mentor_id VARCHAR(50) NOT NULL COMMENT '指导关系标识',
  student_id        CHAR(20)    NOT NULL COMMENT '学生标识',
  mentor_user_id    VARCHAR(50) NOT NULL COMMENT '导师工号/用户标识',
  is_primary        TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否主导师',
  valid_from        DATE        NOT NULL COMMENT '生效日期',
  valid_to          DATE        NULL     COMMENT '失效日期',
  PRIMARY KEY (student_mentor_id),
  KEY idx_grad_mentor_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第13组 研究生导师指导关系表';

-- 8.8 第 15 组 对外交流管理扩展（国内访学返校链、外校原始成绩单、来华留学生、接收导师意见、教师出访状态）
CREATE TABLE IF NOT EXISTS exg_dv_filing (
  filing_id           CHAR(40)     NOT NULL COMMENT '访学备案编号',
  student_id          CHAR(20)     NOT NULL COMMENT '学号',
  host_school         VARCHAR(200) NOT NULL COMMENT '访学院校',
  visit_location      VARCHAR(100) NOT NULL COMMENT '访学地点',
  start_date          DATE         NOT NULL COMMENT '访学开始日期',
  planned_return_date DATE         NOT NULL COMMENT '计划返校日期',
  filing_date         DATE         NOT NULL COMMENT '备案日期',
  filing_status       CHAR(2)      NOT NULL DEFAULT '01' COMMENT '备案状态',
  visit_reason        VARCHAR(500) NULL     COMMENT '访学事由',
  PRIMARY KEY (filing_id),
  KEY idx_exg_dv_filing_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 国内访学备案表';

CREATE TABLE IF NOT EXISTS exg_dv_return_report (
  report_id          VARCHAR(50)  NOT NULL COMMENT '报到编号',
  filing_id          CHAR(40)     NOT NULL COMMENT '访学备案编号',
  student_id         CHAR(20)     NOT NULL COMMENT '学号',
  actual_return_date DATE         NOT NULL COMMENT '实际返校日期',
  report_date        DATE         NOT NULL COMMENT '报到日期',
  report_note        VARCHAR(500) NULL     COMMENT '报到说明',
  PRIMARY KEY (report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 国内访学返校报到表';

CREATE TABLE IF NOT EXISTS exg_dv_return_filing (
  return_filing_id   CHAR(20)    NOT NULL COMMENT '返校备案编号',
  report_id          VARCHAR(50) NOT NULL COMMENT '报到编号',
  student_id         CHAR(20)    NOT NULL COMMENT '学号',
  actual_return_date DATE        NOT NULL COMMENT '实际返校日期',
  filing_date        DATE        NOT NULL COMMENT '备案日期',
  filer              VARCHAR(50) NOT NULL COMMENT '备案人',
  filing_status      CHAR(2)     NOT NULL DEFAULT '00' COMMENT '备案状态：01已备案',
  PRIMARY KEY (return_filing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 国内访学返校备案表';

CREATE TABLE IF NOT EXISTS exg_vst_foreign_transcript (
  foreign_transcript_id VARCHAR(40)  NOT NULL COMMENT '外校成绩单编号',
  student_id            CHAR(20)     NOT NULL COMMENT '学号',
  partner_school        VARCHAR(100) NOT NULL COMMENT '合作院校',
  study_term            VARCHAR(30)  NOT NULL COMMENT '修读学期',
  foreign_course_id     CHAR(30)     NOT NULL COMMENT '外校课程代码',
  foreign_course_name   VARCHAR(100) NOT NULL COMMENT '外校课程名称',
  foreign_credit        DECIMAL(5,1) NOT NULL COMMENT '外校学分',
  foreign_score         DECIMAL(5,2) NOT NULL COMMENT '外校成绩',
  issue_date            DATE         NOT NULL COMMENT '出具日期',
  PRIMARY KEY (foreign_transcript_id),
  KEY idx_exg_ft_stu (student_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 外校原始成绩单明细表';

CREATE TABLE IF NOT EXISTS exg_is_student (
  student_id               CHAR(20)     NOT NULL COMMENT '学号',
  person_name              VARCHAR(100) NOT NULL COMMENT '姓名',
  nationality              VARCHAR(50)  NOT NULL COMMENT '国籍',
  passport_no              VARCHAR(40)  NOT NULL COMMENT '护照号',
  college_name             VARCHAR(100) NOT NULL COMMENT '学院',
  org_id                   CHAR(6)      NOT NULL COMMENT '机构ID',
  major_name               VARCHAR(100) NOT NULL COMMENT '专业',
  major_id                 CHAR(6)      NOT NULL COMMENT '专业ID',
  class_name               VARCHAR(50)  NOT NULL COMMENT '班级',
  degree_level             VARCHAR(20)  NOT NULL COMMENT '培养层次',
  enroll_date              DATE         NOT NULL COMMENT '入学日期',
  expected_graduation_date DATE         NOT NULL COMMENT '预计毕业日期',
  scholarship_program      VARCHAR(50)  NOT NULL COMMENT '奖学金项目',
  register_status          CHAR(2)      NOT NULL DEFAULT '01' COMMENT '注册状态',
  student_status           CHAR(2)      NOT NULL DEFAULT '01' COMMENT '学籍状态',
  PRIMARY KEY (student_id),
  UNIQUE KEY uk_exg_is_passport (passport_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来华学历留学生学籍表';

CREATE TABLE IF NOT EXISTS exg_supervisor_opinion (
  opinion_id     CHAR(20)     NOT NULL COMMENT '意见编号',
  apply_id       VARCHAR(40)  NOT NULL COMMENT '关联申请编号',
  emp_id         CHAR(10)     NOT NULL COMMENT '接收导师工号',
  emp_name       VARCHAR(50)  NOT NULL COMMENT '导师姓名',
  college_name   VARCHAR(100) NOT NULL COMMENT '所属学院',
  org_id         CHAR(6)      NOT NULL COMMENT '机构ID',
  accept_opinion VARCHAR(500) NOT NULL COMMENT '接收意见',
  course_plan    VARCHAR(500) NULL     COMMENT '拟修读课程方案',
  submitted_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (opinion_id),
  KEY idx_exg_sup_apply (apply_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 来校交流生接收导师意见表';

CREATE TABLE IF NOT EXISTS exg_ob_teacher_status (
  status_id      VARCHAR(40)  NOT NULL COMMENT '状态编号',
  emp_id         CHAR(10)     NOT NULL COMMENT '教师编号',
  trip_id        CHAR(24)     NULL     COMMENT '出访编号',
  current_status CHAR(2)      NOT NULL COMMENT '当前状态：02出访/拟出访',
  effective_time DATE         NOT NULL COMMENT '状态生效时间',
  end_at         VARCHAR(40)  NULL     COMMENT '状态结束时间',
  sync_status    CHAR(2)      NOT NULL DEFAULT '00' COMMENT '同步师资管理状态',
  sync_time      DATE         NULL     COMMENT '同步时间',
  sync_error     VARCHAR(500) NULL     COMMENT '同步失败原因',
  PRIMARY KEY (status_id),
  KEY idx_exg_ob_emp (emp_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '第15组 教师因公出访状态表';

-- 跨组只读接口视图：覆盖国际访学（exg_vst_*）与国内访学（exg_dv_*）返校备案资格
CREATE OR REPLACE VIEW v_exchange_credit_ready AS
SELECT d.apply_id AS application_no,
       f.student_id,
       COALESCE(
         (SELECT t.term FROM aca_term t WHERE d.departure_date BETWEEN t.start_date AND t.end_date LIMIT 1),
         '2026-2027-1'
       ) AS term_ref,
       d.partner_school AS partner_school_snapshot,
       CASE WHEN f.filing_status IN ('01', '1', '已备案') THEN 1 ELSE 0 END AS ready_flag,
       'INTL' AS exchange_type,
       f.actual_return_date
FROM exg_vst_return_filing f
JOIN exg_vst_return_report r ON f.report_id = r.report_id
JOIN exg_vst_departure d ON r.departure_id = d.departure_id
UNION ALL
SELECT df.filing_id AS application_no,
       rf.student_id,
       COALESCE(
         (SELECT t.term FROM aca_term t WHERE df.start_date BETWEEN t.start_date AND t.end_date LIMIT 1),
         '2026-2027-1'
       ) AS term_ref,
       df.host_school AS partner_school_snapshot,
       CASE WHEN rf.filing_status IN ('01', '1', '已备案') THEN 1 ELSE 0 END AS ready_flag,
       'DOMESTIC' AS exchange_type,
       rf.actual_return_date
FROM exg_dv_return_filing rf
JOIN exg_dv_return_report rr ON rf.report_id = rr.report_id
JOIN exg_dv_filing df ON rr.filing_id = df.filing_id;

-- =====================================================================
-- 七、本组业务⑥ 6.2 数据备份与恢复演示日志表（非跨组表，保留支撑 6.2）
-- =====================================================================

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
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据备份与恢复日志表（业务⑥ 6.2 本地演示表）';
