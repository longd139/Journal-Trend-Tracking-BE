import sys
sys.stdout.reconfigure(encoding='utf-8')

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE, MSO_CONNECTOR_TYPE
from pptx.oxml.ns import qn

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)

# ═══════════════ COLOR PALETTE (Light theme) ═══════════════
BG_WHITE = RGBColor(0xFF, 0xFF, 0xFF)       # white background
CARD_BG = RGBColor(0xF0, 0xF4, 0xF8)        # light blue-gray card
ACCENT = RGBColor(0x00, 0x8B, 0xD2)         # deep blue
ACCENT2 = RGBColor(0x7B, 0x2F, 0xFF)        # purple
GREEN = RGBColor(0x00, 0x96, 0x4B)          # darker green (readable on white)
ORANGE = RGBColor(0xE0, 0x6B, 0x00)         # darker orange
RED = RGBColor(0xDC, 0x26, 0x26)            # red
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
DARK = RGBColor(0x1A, 0x1A, 0x2E)           # dark text
LIGHT = RGBColor(0x55, 0x55, 0x77)          # lighter text
SUBTLE = RGBColor(0x88, 0x88, 0xAA)         # subtle text
GRAY_CARD = RGBColor(0x66, 0x66, 0x88)
STEP_BG = RGBColor(0xF0, 0xF4, 0xF8)        # same as card bg
DB_COLOR = RGBColor(0xCC, 0xD5, 0xE0)       # lighter cylinder
ACCENT_LIGHT = RGBColor(0xE8, 0xF4, 0xFD)   # light blue tint

def light_slide():
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    bg = slide.background
    bg.fill.solid()
    bg.fill.fore_color.rgb = BG_WHITE
    return slide

def tb(slide, left, top, width, height, text, size=14, bold=False, color=DARK, align=PP_ALIGN.LEFT, font='Segoe UI'):
    txBox = slide.shapes.add_textbox(Inches(left), Inches(top), Inches(width), Inches(height))
    tf = txBox.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = text
    p.font.size = Pt(size)
    p.font.bold = bold
    p.font.color.rgb = color
    p.font.name = font
    p.alignment = align
    return tf

def accent_line(slide, left, top):
    line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(left), Inches(top), Inches(0.05), Inches(0.45))
    line.fill.solid(); line.fill.fore_color.rgb = ACCENT; line.line.fill.background()

def title_bar(slide, title, subtitle=None):
    accent_line(slide, 0.6, 0.5)
    tb(slide, 0.9, 0.42, 11.5, 0.7, title, size=30, bold=True)
    if subtitle:
        tb(slide, 0.9, 1.05, 11.5, 0.35, subtitle, size=15, color=LIGHT)
    # bottom separator line
    sep = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.6), Inches(1.45), Inches(12.1), Inches(0.01))
    sep.fill.solid(); sep.fill.fore_color.rgb = RGBColor(0x33, 0x33, 0x55); sep.line.fill.background()

def footer(slide, num=None):
    tb(slide, 0.5, 7.05, 12, 0.3, "SCITRACK — AI-Powered Academic Research Trend Tracking", size=9, color=SUBTLE)
    if num:
        tb(slide, 12.4, 7.05, 0.7, 0.3, str(num), size=9, color=SUBTLE, align=PP_ALIGN.RIGHT)

def card(slide, left, top, w, h, icon, title, bullets, accent_color=ACCENT):
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(left), Inches(top), Inches(w), Inches(h))
    shape.fill.solid(); shape.fill.fore_color.rgb = CARD_BG
    shape.line.color.rgb = RGBColor(0x33, 0x33, 0x55); shape.line.width = Pt(0.5)
    tf = shape.text_frame; tf.word_wrap = True
    tf.margin_left = Inches(0.15); tf.margin_right = Inches(0.1); tf.margin_top = Inches(0.1)
    p = tf.paragraphs[0]; p.text = f"{icon}  {title}"
    p.font.size = Pt(14); p.font.bold = True; p.font.color.rgb = accent_color; p.font.name = 'Segoe UI'
    for b in bullets:
        bp = tf.add_paragraph(); bp.text = f"• {b}"
        bp.font.size = Pt(11); bp.font.color.rgb = LIGHT; bp.font.name = 'Segoe UI'
        bp.space_before = Pt(3)
    return shape

# ═══════════════ FLOW HELPERS ═══════════════
def flow_step(slide, left, top, w, h, icon, label, sublabel=None, color=ACCENT):
    """A rounded rectangle step in a flow diagram"""
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(left), Inches(top), Inches(w), Inches(h))
    shape.fill.solid(); shape.fill.fore_color.rgb = STEP_BG
    shape.line.color.rgb = color; shape.line.width = Pt(1)
    tf = shape.text_frame; tf.word_wrap = True; tf.margin_left = Inches(0.05); tf.margin_top = Inches(0.05)
    p = tf.paragraphs[0]; p.text = icon; p.font.size = Pt(20); p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
    bp = tf.add_paragraph(); bp.text = label; bp.font.size = Pt(12); bp.font.bold = True
    bp.font.color.rgb = color; bp.font.name = 'Segoe UI'; bp.alignment = PP_ALIGN.CENTER
    if sublabel:
        sp = tf.add_paragraph(); sp.text = sublabel; sp.font.size = Pt(9)
        sp.font.color.rgb = LIGHT; sp.font.name = 'Segoe UI'; sp.alignment = PP_ALIGN.CENTER
    return shape

def flow_arrow(slide, left, top, w=0.5, h=0.3):
    """Text arrow between flow steps"""
    tb(slide, left, top, w, h, "→", size=22, bold=True, color=ACCENT, align=PP_ALIGN.CENTER)

def flow_arrow_cyan(slide, left, top, w=0.4, h=0.3):
    tb(slide, left, top, w, h, "→", size=20, bold=True, color=ACCENT, align=PP_ALIGN.CENTER)

def db_cylinder(slide, left, top, w, h, label, sublabel=""):
    """A database box"""
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(left), Inches(top), Inches(w), Inches(h))
    shape.fill.solid(); shape.fill.fore_color.rgb = DB_COLOR
    shape.line.color.rgb = ACCENT; shape.line.width = Pt(1)
    tf = shape.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; p.text = label; p.font.size = Pt(12); p.font.bold = True
    p.font.color.rgb = DARK; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
    if sublabel:
        sp = tf.add_paragraph(); sp.text = sublabel; sp.font.size = Pt(8)
        sp.font.color.rgb = LIGHT; sp.font.name = 'Segoe UI'; sp.alignment = PP_ALIGN.CENTER
    return shape

def actor_circle(slide, left, top, icon, label, color=ACCENT):
    """Circle with icon representing an actor"""
    shape = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(left), Inches(top), Inches(0.9), Inches(0.9))
    shape.fill.solid(); shape.fill.fore_color.rgb = CARD_BG
    shape.line.color.rgb = color; shape.line.width = Pt(1.5)
    tf = shape.text_frame; tf.word_wrap = True
    p = tf.paragraphs[0]; p.text = icon; p.font.size = Pt(24); p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
    tb(slide, left - 0.5, top + 1.0, 1.9, 0.35, label, size=11, bold=True, color=color, align=PP_ALIGN.CENTER)

def simple_card(slide, left, top, w, h, title, content_lines, title_color=ACCENT, text_color=LIGHT):
    """Card with title and text lines"""
    shape = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(left), Inches(top), Inches(w), Inches(h))
    shape.fill.solid(); shape.fill.fore_color.rgb = CARD_BG
    shape.line.color.rgb = RGBColor(0x33, 0x33, 0x55); shape.line.width = Pt(0.5)
    tf = shape.text_frame; tf.word_wrap = True
    tf.margin_left = Inches(0.12); tf.margin_right = Inches(0.08); tf.margin_top = Inches(0.08)
    p = tf.paragraphs[0]; p.text = title; p.font.size = Pt(13); p.font.bold = True
    p.font.color.rgb = title_color; p.font.name = 'Segoe UI'
    for line in content_lines:
        bp = tf.add_paragraph(); bp.text = line; bp.font.size = Pt(10)
        bp.font.color.rgb = text_color; bp.font.name = 'Segoe UI'; bp.space_before = Pt(2)
    return shape


# ═════════════════════════════════════════════════════
# SLIDE 1 — TRANG BÌA
# ═════════════════════════════════════════════════════
slide = light_slide()
# Logo placeholder
logo = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(5.4), Inches(0.7), Inches(2.5), Inches(2.5))
logo.fill.solid(); logo.fill.fore_color.rgb = CARD_BG
logo.line.color.rgb = ACCENT; logo.line.width = Pt(2)
tf = logo.text_frame
p = tf.paragraphs[0]; p.text = "SCITRACK"; p.font.size = Pt(18); p.font.bold = True
p.font.color.rgb = ACCENT; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
p2 = tf.add_paragraph(); p2.text = "🔍 📈"; p2.font.size = Pt(26); p2.alignment = PP_ALIGN.CENTER

tb(slide, 1.5, 3.5, 10.3, 0.8, "SCITRACK — AI-Powered Academic Research Trend Tracking", size=34, bold=True, align=PP_ALIGN.CENTER)
tb(slide, 1.5, 4.2, 10.3, 0.5, "Hệ thống theo dõi xu hướng nghiên cứu khoa học ứng dụng trí tuệ nhân tạo", size=17, color=LIGHT, align=PP_ALIGN.CENTER)

div = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(4.5), Inches(4.85), Inches(4.3), Inches(0.025))
div.fill.solid(); div.fill.fore_color.rgb = ACCENT; div.line.fill.background()

tb(slide, 2, 5.15, 9.3, 0.35, "Nhóm: [Tên nhóm]    |    GVHD: [Tên giảng viên]", size=15, color=LIGHT, align=PP_ALIGN.CENTER)
tb(slide, 2, 5.6, 9.3, 0.35, "Leader: [Họ tên]  •  [TV2]  •  [TV3]  •  [TV4]", size=13, color=SUBTLE, align=PP_ALIGN.CENTER)
tb(slide, 2, 6.1, 9.3, 0.35, "[Học kỳ / Năm học]", size=13, color=SUBTLE, align=PP_ALIGN.CENTER)

# ═════════════════════════════════════════════════════
# SLIDE 2 — INTRODUCTION (Làm gì / Không làm gì)
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "SCITRACK là gì?", "Nền tảng web cho nhà nghiên cứu, sinh viên, giảng viên")
footer(slide, 2)

# Left: App làm gì
left_card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.4), Inches(1.7), Inches(6.0), Inches(5.1))
left_card.fill.solid(); left_card.fill.fore_color.rgb = CARD_BG
left_card.line.color.rgb = GREEN; left_card.line.width = Pt(1)
tf = left_card.text_frame; tf.word_wrap = True
tf.margin_left = Inches(0.2); tf.margin_right = Inches(0.15); tf.margin_top = Inches(0.15)
p = tf.paragraphs[0]; p.text = "✅  SCITRACK LÀM GÌ"; p.font.size = Pt(18); p.font.bold = True
p.font.color.rgb = GREEN; p.font.name = 'Segoe UI'

features = [
    ("🔍", "Tìm kiếm bài báo khoa học", "Theo từ khóa, tác giả, tạp chí, lĩnh vực"),
    ("📊", "Theo dõi xu hướng nghiên cứu", "Biểu đồ thống kê publication trend"),
    ("🤖", "AI tóm tắt & phân tích", "Abstract, methodology, batch analysis (DeepSeek)"),
    ("🕸️", "Knowledge Graph", "Đồ thị quan hệ Paper ↔ Keyword (Neo4j)"),
    ("🔖", "Bookmark & Bộ sưu tập", "Lưu paper, phân loại theo collection"),
    ("🔔", "Theo dõi tác giả / lĩnh vực", "Nhận thông báo real-time khi có paper mới"),
    ("💡", "Recommendation", "Gợi ý paper dựa trên lịch sử tìm kiếm"),
]
for i, (icon, title, desc) in enumerate(features):
    bp = tf.add_paragraph(); bp.text = ""; bp.font.size = Pt(4); bp.space_before = Pt(2)
    bp = tf.add_paragraph(); bp.text = f"{icon}  {title}"; bp.font.size = Pt(13); bp.font.bold = True
    bp.font.color.rgb = WHITE; bp.font.name = 'Segoe UI'
    bp = tf.add_paragraph(); bp.text = f"        {desc}"; bp.font.size = Pt(10)
    bp.font.color.rgb = LIGHT; bp.font.name = 'Segoe UI'

# Right: App KHÔNG làm gì
right_card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(6.8), Inches(1.7), Inches(6.0), Inches(5.1))
right_card.fill.solid(); right_card.fill.fore_color.rgb = RGBColor(0x25, 0x12, 0x12)
right_card.line.color.rgb = RED; right_card.line.width = Pt(1)
tf = right_card.text_frame; tf.word_wrap = True
tf.margin_left = Inches(0.2); tf.margin_right = Inches(0.15); tf.margin_top = Inches(0.15)
p = tf.paragraphs[0]; p.text = "⛔  SCITRACK KHÔNG LÀM GÌ"; p.font.size = Pt(18); p.font.bold = True
p.font.color.rgb = RED; p.font.name = 'Segoe UI'

not_features = [
    ("❌", "Upload & publish paper", "Đây là hệ thống tra cứu, không phải journal submission system"),
    ("❌", "Viết báo tự động", "AI chỉ tóm tắt, không sinh nội dung nghiên cứu mới"),
    ("❌", "Thay thế Google Scholar / Scopus", "Công cụ phân tích trend chuyên sâu, không phải search engine toàn cầu"),
    ("❌", "Mạng xã hội học thuật", "Không bình luận public, không feed, không social networking"),
]
for i, (icon, title, desc) in enumerate(not_features):
    bp = tf.add_paragraph(); bp.text = ""; bp.font.size = Pt(8); bp.space_before = Pt(2)
    bp = tf.add_paragraph(); bp.text = f"{icon}  {title}"; bp.font.size = Pt(13); bp.font.bold = True
    bp.font.color.rgb = ORANGE; bp.font.name = 'Segoe UI'
    bp = tf.add_paragraph(); bp.text = f"         {desc}"; bp.font.size = Pt(10)
    bp.font.color.rgb = LIGHT; bp.font.name = 'Segoe UI'

# ═════════════════════════════════════════════════════
# SLIDE 3 — 4 ACTOR TYPES
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Phân quyền Người Dùng", "4 Actor Types với quyền hạn tăng dần")
footer(slide, 3)

actors = [
    ("👤", "Guest", GRAY_CARD, [
        "Xem danh sách paper",
        "Search cơ bản",
        "Filter cơ bản",
        "Xem Knowledge Graph",
        "Xem Publication Trend",
    ]),
    ("🎓", "Academic User", GREEN, [
        "Tất cả quyền Guest",
        "Bookmark + Collection",
        "Theo dõi tác giả / từ khóa",
        "Lịch sử search & đọc",
        "Giới hạn usage hàng tháng",
    ]),
    ("🔬", "Researcher", ACCENT2, [
        "Tất cả quyền Academic",
        "Advanced Filter",
        "AI Summarization",
        "Batch Analysis",
        "Unlimited Usage",
    ]),
    ("⚙️", "Admin", ORANGE, [
        "Quản lý người dùng",
        "Manual Sync dữ liệu",
        "Dashboard tổng quan",
        "Audit Log",
        "Duyệt PDF Request",
    ]),
]

for i, (icon, name, color, bullets) in enumerate(actors):
    card(slide, 0.5 + i * 3.15, 1.8, 2.95, 5.0, icon, name, bullets, accent_color=color)

# ═════════════════════════════════════════════════════
# SLIDE 4 — GUEST FEATURES (card grid)
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Tính năng — Guest", "Người dùng chưa đăng nhập — chỉ xem, không lưu trữ")
footer(slide, 4)

guest_features = [
    ("🔍", "Search Papers", "Tìm kiếm bài báo\ntheo từ khóa"),
    ("📋", "Paper List", "Danh sách kết quả\ncó phân trang"),
    ("🔬", "Paper Detail", "Abstract, authors,\njournal, keywords"),
    ("🕸️", "Knowledge Graph", "Đồ thị quan hệ\nPaper ↔ Keyword"),
    ("📊", "Publication Trends", "Biểu đồ xu hướng\ntheo năm"),
    ("📈", "Trending Keywords", "Từ khóa đang hot,\nresearch landscape"),
    ("🎯", "Filter cơ bản", "Lọc theo năm,\nlĩnh vực, tạp chí"),
]
for i, (icon, title, desc) in enumerate(guest_features):
    col = i % 4
    row = i // 4
    simple_card(slide, 0.4 + col * 3.15, 1.8 + row * 2.6, 2.95, 2.3, f"{icon}  {title}", desc.split('\n'), title_color=ACCENT)

tb(slide, 0.5, 6.8, 12, 0.3, "⚠️ Guest KHÔNG cần đăng nhập — chỉ xem, không bookmark hay lưu được gì", size=12, color=ORANGE)

# ═════════════════════════════════════════════════════
# SLIDE 5 — ACADEMIC USER FEATURES
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Tính năng — Academic User", "Guest + cá nhân hóa & lưu trữ")
footer(slide, 5)

acad_features = [
    ("🔖", "Bookmark Paper", "Lưu paper vào\ndanh sách yêu thích"),
    ("📁", "Collections", "Tạo bộ sưu tập,\nphân loại paper"),
    ("👤", "Follow Author", "Theo dõi tác giả,\nnhận thông báo"),
    ("🏷️", "Follow Keyword", "Theo dõi từ khóa,\nnhận paper mới"),
    ("⭐", "Rate Paper", "Đánh giá paper\n1-5 sao"),
    ("📥", "PDF Request", "Yêu cầu PDF nếu\npaper không có sẵn"),
    ("🔔", "Notifications", "Thông báo real-time\nqua SSE"),
    ("📊", "Usage Stats", "Xem lượt search/view\ncòn lại trong tháng"),
    ("📜", "Search History", "Xem lại lịch sử\ntìm kiếm"),
    ("📖", "Reading History", "Lịch sử paper\nđã đọc"),
    ("⬆️", "Upgrade", "Nâng cấp lên\nResearcher (unlimited)"),
]
for i, (icon, title, desc) in enumerate(acad_features):
    col = i % 4
    row = i // 4
    simple_card(slide, 0.3 + col * 3.2, 1.75 + row * 1.8, 3.0, 1.55, f"{icon}  {title}", desc.split('\n'), title_color=GREEN)

tb(slide, 0.5, 6.95, 12, 0.3, "⚠️ Academic User có giới hạn search/view hàng tháng (configurable trong SystemConfig)", size=11, color=ORANGE)

# ═════════════════════════════════════════════════════
# SLIDE 6 — RESEARCHER + ADMIN (2 columns)
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Tính năng — Researcher & Admin", "Nâng cao & Quản trị hệ thống")
footer(slide, 6)

simple_card(slide, 0.4, 1.7, 6.0, 5.2, "🔬  RESEARCHER", [
    "🎛️  Advanced Filter — citation count, h-index, methodology, OA",
    "🤖  AI Summarization — tóm tắt abstract 2-3 câu",
    "🧪  Methodology Extraction — trích xuất phương pháp NC",
    "📦  Batch Analysis — phân tích cross-paper so sánh",
    "📝  Generate Report — báo cáo tổng hợp từ kết quả search",
    "♾️  Unlimited Usage — không giới hạn search/view",
], title_color=ACCENT2)

simple_card(slide, 6.8, 1.7, 6.0, 5.2, "⚙️  ADMIN", [
    "👥  User Management — enable/disable, đổi role",
    "⚙️  System Config — cấu hình giới hạn usage, API key...",
    "🔄  Manual Sync — trigger OpenAlex / Semantic Scholar",
    "📋  Audit Log — nhật ký hoạt động hệ thống",
    "📊  Dashboard — thống kê users, papers, sync status",
    "📄  Duyệt PDF Request — quản lý yêu cầu PDF từ user",
], title_color=ORANGE)

# ═════════════════════════════════════════════════════
# SLIDE 7 — TECHNOLOGY STACK
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Technology Stack", "Kiến trúc hệ thống & công nghệ sử dụng")
footer(slide, 7)

stack = [
    ("🖥️  FRONTEND", GREEN, ["React 19 + Vite 8", "TailwindCSS 4", "Axios HTTP Client", "React Router v7"]),
    ("⚙️  BACKEND", ACCENT, ["Spring Boot 3.5.14", "Java 21 + Maven", "JWT + Spring Security", "SSE (Server-Sent Events)"]),
    ("🗄️  DATABASE & INFRA", ACCENT2, ["SQL Server 2022 (Docker)", "Neo4j Aura (Graph DB)", "Docker Compose", "Git + GitHub Flow"]),
    ("🌐  EXTERNAL APIs", ORANGE, ["OpenAlex API (data source)", "Semantic Scholar API", "DeepSeek v4 Pro (AI)", "ai-box.vn (OpenAI-compat)"]),
]
for i, (title, color, items) in enumerate(stack):
    simple_card(slide, 0.4 + i * 3.2, 1.7, 3.0, 3.0, title, items, title_color=color)

# Architecture flow
arch_l = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.8), Inches(5.2), Inches(2.5), Inches(1.2))
arch_l.fill.solid(); arch_l.fill.fore_color.rgb = CARD_BG
arch_l.line.color.rgb = GREEN; arch_l.line.width = Pt(1)
tf = arch_l.text_frame; tf.word_wrap = True
p = tf.paragraphs[0]; p.text = "🖥️  React Frontend"; p.font.size = Pt(14); p.font.bold = True; p.font.color.rgb = GREEN; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
bp = tf.add_paragraph(); bp.text = "localhost:5173"; bp.font.size = Pt(11); bp.font.color.rgb = LIGHT; bp.font.name = 'Segoe UI'; bp.alignment = PP_ALIGN.CENTER

arch_m = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(4.5), Inches(5.2), Inches(4.0), Inches(1.2))
arch_m.fill.solid(); arch_m.fill.fore_color.rgb = CARD_BG
arch_m.line.color.rgb = ACCENT; arch_m.line.width = Pt(1)
tf = arch_m.text_frame; tf.word_wrap = True
p = tf.paragraphs[0]; p.text = "⚙️  Spring Boot REST API"; p.font.size = Pt(14); p.font.bold = True; p.font.color.rgb = ACCENT; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER
bp = tf.add_paragraph(); bp.text = "Controllers → Services → JPA / Neo4jClient"; bp.font.size = Pt(11); bp.font.color.rgb = LIGHT; bp.font.name = 'Segoe UI'; bp.alignment = PP_ALIGN.CENTER

arch_r1 = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(9.8), Inches(5.2), Inches(3.1), Inches(0.52))
arch_r1.fill.solid(); arch_r1.fill.fore_color.rgb = CARD_BG
arch_r1.line.color.rgb = ACCENT2; arch_r1.line.width = Pt(1)
tf = arch_r1.text_frame
p = tf.paragraphs[0]; p.text = "🗄️  SQL Server (Structured Data)"; p.font.size = Pt(12); p.font.color.rgb = ACCENT2; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER

arch_r2 = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(9.8), Inches(5.85), Inches(3.1), Inches(0.52))
arch_r2.fill.solid(); arch_r2.fill.fore_color.rgb = CARD_BG
arch_r2.line.color.rgb = ACCENT2; arch_r2.line.width = Pt(1)
tf = arch_r2.text_frame
p = tf.paragraphs[0]; p.text = "🕸️  Neo4j (Graph Data)"; p.font.size = Pt(12); p.font.color.rgb = ACCENT2; p.font.name = 'Segoe UI'; p.alignment = PP_ALIGN.CENTER

flow_arrow(slide, 3.35, 5.55, 1.1, 0.4)
flow_arrow(slide, 8.55, 5.55, 1.2, 0.4)

tb(slide, 0.5, 6.7, 12, 0.3, "CI/CD: Git + GitHub Flow (main ← develop ← feature/*)  |  Build: Maven (BE) / Vite (FE)  |  Container: Docker Compose", size=11, color=SUBTLE)

# ═════════════════════════════════════════════════════
# SLIDE 8 — DEMO SCENARIO #1: Guest search
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Demo #1: Guest tìm kiếm \"Climate Change\"", "Tìm kiếm & xem paper — KHÔNG cần đăng nhập")
footer(slide, 8)

# Flow diagram: Guest → Search → Neo4j+SQL → Paper List → Paper Detail
y_flow = 2.5
actor_circle(slide, 0.6, y_flow, "👤", "Guest", GRAY_CARD)
flow_arrow(slide, 1.7, y_flow + 0.2)

flow_step(slide, 2.2, y_flow, 2.2, 2.1, "🔍", "Search Page", 'Nhập "Climate Change"\nvào ô tìm kiếm', ACCENT)
flow_arrow(slide, 4.5, y_flow + 0.2)

flow_step(slide, 5.0, y_flow, 2.3, 2.1, "⚡", "Backend Xử Lý", "Neo4j graph lookup\n→ SQL data fetch\n< 1 giây từ cache", GREEN)
flow_arrow(slide, 7.4, y_flow + 0.2)

flow_step(slide, 7.9, y_flow, 2.2, 2.1, "📄", "Paper List", "Danh sách kết quả\ncó phân trang\nđúng keyword", ACCENT)
flow_arrow(slide, 10.2, y_flow + 0.2)

flow_step(slide, 10.7, y_flow, 2.1, 2.1, "📖", "Paper Detail", "Abstract, Authors,\nJournal, Keywords\n+ Knowledge Graph", ACCENT2)

# Result box
result_box = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.5), Inches(5.3), Inches(12.3), Inches(1.3))
result_box.fill.solid(); result_box.fill.fore_color.rgb = RGBColor(0x12, 0x28, 0x12)
result_box.line.color.rgb = GREEN; result_box.line.width = Pt(1)
tf = result_box.text_frame; tf.word_wrap = True; tf.margin_left = Inches(0.2); tf.margin_top = Inches(0.08)
p = tf.paragraphs[0]; p.text = "✅  Kết quả: Guest xem được toàn bộ paper + Knowledge Graph, KHÔNG cần login. Search < 1s từ cache Neo4j."; p.font.size = Pt(14); p.font.bold = True; p.font.color.rgb = GREEN; p.font.name = 'Segoe UI'

# ═════════════════════════════════════════════════════
# SLIDE 9 — DEMO SCENARIO #2: Academic User Bookmark
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Demo #2: Academic User Bookmark & Collection", "Đăng nhập → Bookmark paper → Tạo bộ sưu tập")
footer(slide, 9)

# Flow: Academic → Login → Search → Bookmark → Collection → DB
y_flow = 2.5
actor_circle(slide, 0.5, y_flow, "🎓", "Academic\nUser", GREEN)
flow_arrow(slide, 1.6, y_flow + 0.2)

flow_step(slide, 2.0, y_flow, 1.9, 2.1, "🔑", "Login", "Email + Password\n→ Nhận JWT Token", GREEN)
flow_arrow(slide, 4.0, y_flow + 0.2)

flow_step(slide, 4.4, y_flow, 1.9, 2.1, "🔍", "Search", '"Ocean Economy"\n→ Chọn paper từ\nkết quả', ACCENT)
flow_arrow(slide, 6.4, y_flow + 0.2)

flow_step(slide, 6.8, y_flow, 1.9, 2.1, "⭐", "Bookmark", "Click nút Bookmark\ntrên Paper Detail", ORANGE)
flow_arrow(slide, 8.8, y_flow + 0.2)

flow_step(slide, 9.2, y_flow, 1.9, 2.1, "📁", "Chọn Collection", '"Ocean Research"\n(tạo mới nếu\nchưa có)', ACCENT2)
flow_arrow(slide, 11.2, y_flow + 0.2)

db_cylinder(slide, 11.6, y_flow + 0.3, 1.3, 1.6, "🗄️ SQL Server", "INSERT BOOKMARK\n(user, paper,\ncollection_id)")

# Note box
note = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(0.5), Inches(5.3), Inches(12.3), Inches(1.3))
note.fill.solid(); note.fill.fore_color.rgb = CARD_BG
note.line.color.rgb = ACCENT; note.line.width = Pt(1)
tf = note.text_frame; tf.word_wrap = True; tf.margin_left = Inches(0.2); tf.margin_top = Inches(0.08)
p = tf.paragraphs[0]; p.text = "💡  JWT Stateless — token lưu ở client, không session server-side. Mỗi user có Bookmark + Collection riêng."; p.font.size = Pt(15); p.font.bold = True; p.font.color.rgb = ACCENT; p.font.name = 'Segoe UI'

# ═════════════════════════════════════════════════════
# SLIDE 10 — DEMO SCENARIO #3: Researcher AI
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Demo #3: Researcher AI Summarization", "AI tóm tắt paper + Batch Analysis (DeepSeek v4 Pro)")
footer(slide, 10)

# Upper flow: Researcher → Paper → AI → DeepSeek → Result
y_top = 2.2
actor_circle(slide, 0.4, y_top, "🔬", "Researcher", ACCENT2)
flow_arrow(slide, 1.5, y_top + 0.2)

flow_step(slide, 1.9, y_top, 2.0, 1.9, "📖", "Mở Paper", '"From Grey to Blue:\nThe Ocean Economy"\n(Gourvenec, 2024)', ACCENT)
flow_arrow(slide, 4.0, y_top + 0.2)

flow_step(slide, 4.4, y_top, 2.0, 1.9, "🤖", "Gọi AI\nSummarize", "Prompt: summarize\nabstract in\n2-3 sentences", ACCENT2)
flow_arrow(slide, 6.5, y_top + 0.2)

flow_step(slide, 6.9, y_top, 2.0, 1.9, "☁️", "DeepSeek API", "deepseek-v4-pro\nqua ai-box.vn\n(OpenAI-compat)", ORANGE)
flow_arrow(slide, 9.0, y_top + 0.2)

flow_step(slide, 9.4, y_top, 3.5, 1.9, "📝", "Kết Quả AI", 'aiSummary: "This research addresses the urgent need to transition..."\nmethodology: "scenario analysis"', GREEN)

# Lower row: Batch + Cache
y_bot = 4.7
flow_step(slide, 1.9, y_bot, 2.5, 1.6, "📦", "Batch Analysis", "Chọn 2+ papers\n→ AI so sánh\ncross-paper insight", ACCENT2)
flow_arrow(slide, 4.5, y_bot + 0.2)

flow_step(slide, 4.9, y_bot, 2.5, 1.6, "🧠", "Cross-Paper\nComparative", "Common themes,\ncontradictions,\nresearch gaps", ACCENT)
flow_arrow(slide, 7.5, y_bot + 0.2)

flow_step(slide, 7.9, y_bot, 2.5, 1.6, "⏱️", "Cache Strategy", "L1: Memory (1h TTL)\nL2: DB (AiSummary)\nL3: AI API call", GREEN)
flow_arrow(slide, 10.5, y_bot + 0.2)

flow_step(slide, 10.9, y_bot, 2.0, 1.6, "🛡️", "Graceful\nFallback", "AI lỗi → field = null\nApp vẫn 200 OK\nKhông crash", ORANGE)

# ═════════════════════════════════════════════════════
# SLIDE 11 — DEMO SCENARIO #4: Admin
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Demo #4: Admin Quản lý & Sync", "Quản lý người dùng + Manual Sync dữ liệu từ OpenAlex")
footer(slide, 11)

# Upper flow: User management
y_top2 = 2.0
actor_circle(slide, 0.3, y_top2, "⚙️", "Admin", ORANGE)
flow_arrow(slide, 1.4, y_top2 + 0.2)

flow_step(slide, 1.8, y_top2, 1.9, 1.7, "📊", "Dashboard", "Tổng quan:\nusers, papers,\nsync logs", ORANGE)
flow_arrow(slide, 3.8, y_top2 + 0.2)

flow_step(slide, 4.2, y_top2, 1.9, 1.7, "👥", "User Mgmt", "Xem danh sách\nfilter theo role\nchọn user", ORANGE)
flow_arrow(slide, 6.2, y_top2 + 0.2)

flow_step(slide, 6.6, y_top2, 1.9, 1.7, "🚫", "Disable / Đổi Role", "Disable → ko login\nđổi Academic →\nResearcher", RED)
flow_arrow(slide, 8.6, y_top2 + 0.2)

db_cylinder(slide, 9.0, y_top2 + 0.35, 1.3, 1.2, "🗄️ SQL Server", "UPDATE USERS\nSET enabled=false")

# Lower flow: Data Sync
y_bot2 = 4.3
actor_circle(slide, 0.3, y_bot2, "⚙️", "Admin", ORANGE)
flow_arrow(slide, 1.4, y_bot2 + 0.2)

flow_step(slide, 1.8, y_bot2, 1.9, 1.7, "🔄", "Trigger Sync", 'Keyword:\n"Climate Change"\nmanual trigger', ACCENT)
flow_arrow(slide, 3.8, y_bot2 + 0.2)

flow_step(slide, 4.2, y_bot2, 1.9, 1.7, "🌐", "OpenAlex API", "Fetch works\npaginated\ndeduplicate DOI", ACCENT2)
flow_arrow(slide, 6.2, y_bot2 + 0.2)

flow_step(slide, 6.6, y_bot2, 1.9, 1.7, "📡", "SSE Progress", "Real-time sync\ntiến trình gửi\nvề frontend", GREEN)
flow_arrow(slide, 8.6, y_bot2 + 0.2)

db_cylinder(slide, 9.0, y_bot2 + 0.35, 1.3, 1.2, "🗄️ SQL + 🕸️ Neo4j", "INSERT papers\nCREATE graph nodes")

# Extra CRUD note
tb(slide, 11.0, 4.2, 2.0, 3.0, "📋 CRUD râu ria\n(nếu còn thời gian):\n\n• CRUD User\n• CRUD Collection\n• CRUD Follow\n• CRUD Notification", size=10, color=SUBTLE)

# ═════════════════════════════════════════════════════
# SLIDE 12 — CHECKLIST CHUẨN BỊ
# ═════════════════════════════════════════════════════
slide = light_slide()
title_bar(slide, "Chuẩn bị Demo — Checklist", "Những thứ cần chuẩn bị trước buổi thuyết trình")
footer(slide, 12)

checklist_cards = [
    ("📱  TÀI KHOẢN DEMO", GREEN, [
        "4 accounts sẵn sàng:",
        "• admin@scitrack.com",
        "• researcher@scitrack.com",
        "• academic@scitrack.com",
        "• Guest (không cần login)",
        "Ghi sẵn Notepad email+password",
    ]),
    ("📊  DATA MẪU", ACCENT, [
        "10-15 papers thật từ OpenAlex:",
        "• Environmental Science",
        "• Computer Science",
        "• Medicine...",
        'Tên tác giả THẬT:',
        "• Gourvenec, Susan",
        "• Smith, John...",
        'KHÔNG "asdf", Lorem ipsum!',
    ]),
    ("💻  KỸ THUẬT", ACCENT2, [
        "☐ Data đã sync sẵn trong DB",
        "☐ App chạy trên mọi máy",
        "☐ Có máy dự phòng",
        "☐ Slide backup mọi máy",
        "☐ KHÔNG demo sync real-time",
    ]),
    ("🎯  DEMO", ORANGE, [
        "☐ Đã test hết 4 scenario",
        "☐ Alt+Tab thuần thục",
        "☐ Notepad mở sẵn text CRUD",
        "☐ Demo theo thứ tự:",
        "   Guest → Academic →",
        "   Researcher → Admin",
    ]),
]

for i, (title, color, lines) in enumerate(checklist_cards):
    simple_card(slide, 0.3 + i * 3.25, 1.7, 3.05, 5.0, title, lines, title_color=color)

tb(slide, 0.5, 6.85, 12, 0.3, "💡 Mẹo: Để app + Notepad mở sẵn 2 màn hình. Alt+Tab chuyển nhanh giữa browser và Notepad.", size=12, color=GREEN)

# ═════════════════════════════════════════════════════
# SLIDE 13 — THANK YOU
# ═════════════════════════════════════════════════════
slide = light_slide()
tb(slide, 1, 2.0, 11.3, 1, "Cảm ơn Thầy/Cô và các bạn đã lắng nghe!", size=40, bold=True, align=PP_ALIGN.CENTER)
tb(slide, 1, 3.2, 11.3, 0.7, "SCITRACK — AI-Powered Academic Research Trend Tracking", size=18, color=ACCENT, align=PP_ALIGN.CENTER)

div = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(4.5), Inches(4.1), Inches(4.3), Inches(0.025))
div.fill.solid(); div.fill.fore_color.rgb = ACCENT; div.line.fill.background()

tb(slide, 1, 4.5, 11.3, 0.6, "Q&A", size=30, bold=True, align=PP_ALIGN.CENTER)
tb(slide, 1, 5.5, 11.3, 0.5, "Nhóm [Tên nhóm]  |  [Học kỳ / Năm học]", size=15, color=SUBTLE, align=PP_ALIGN.CENTER)

# ── SAVE ──
output_path = r"C:\Users\ADMIN\SRC\Journal-Trend-Tracking-BE\SCITRACK_Presentation.pptx"
prs.save(output_path)
print(f"DONE → {output_path}")
print(f"Slides: {len(prs.slides)}")
