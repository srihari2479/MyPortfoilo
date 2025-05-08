import pdfplumber
import re
import pandas as pd

def extract_student_data(pdf_path):
    students_data = []

    with pdfplumber.open(pdf_path) as pdf:
        total_pages = len(pdf.pages)

        for i in range(0, total_pages, 2):
            if i + 1 >= total_pages:
                break
            page1 = pdf.pages[i]
            page2 = pdf.pages[i + 1]
            text1 = page1.extract_text() or ""
            text2 = page2.extract_text() or ""

            # Extract Roll Number
            roll_match = re.search(r'\d{2}U\d{2}A\d{4}', text1)
            roll_number = roll_match.group(0) if roll_match else ""

            # Extract Name
            name_match = re.search(r'Name:\s*(.*?)\s*Report Date:', text1, re.DOTALL)
            name = name_match.group(1).strip() if name_match else ""

            # Extract CGPA and Percentage
            cgpa_match = re.search(r'Secured CGPA:\s*([\d.]+)', text2)
            cgpa = float(cgpa_match.group(1)) if cgpa_match else 0.0

            percentage_match = re.search(r'Equivalent Percentage:\s*([\d.]+)', text2)
            percentage = float(percentage_match.group(1)) if percentage_match else (cgpa * 10) - 7.5

            # Extract backlogs (only 'F' grades)
            backlog_courses = []
            for page in [page1, page2]:
                tables = page.extract_tables()
                for table in tables:
                    for row in table:
                        if not row or not row[0]:
                            continue
                        sn = row[0].strip()
                        if sn.isdigit():
                            grade = row[3].strip() if row[3] else ""
                            if grade == 'F':
                                course_name = row[2].replace('\n', ' ').strip()
                                backlog_courses.append(course_name)

            backlog_count = len(backlog_courses)
            backlog_info = f"{backlog_count} - [{', '.join(backlog_courses)}]" if backlog_count > 0 else "0 - []"

            students_data.append({
                'ROLL_NUMBER': roll_number,
                'NAME': name,
                'BACKLOGS': backlog_info,
                'CGPA': cgpa,
                'PERCENTAGE': round(percentage, 2)
            })

    return students_data