# Export Functionality Design

## 📋 Overview

Comprehensive export functionality for migration reports in multiple formats, supporting both CLI and web UI access.

---

## 🎯 Export Formats

### 1. **PDF Reports** (Recommended for Executive/Client Reports)
**Use Cases:**
- Executive summaries
- Client presentations
- Formal documentation
- Email attachments

**Features:**
- Professional formatting with company branding
- Charts and graphs embedded
- Multi-page support
- Table of contents
- Page numbers and headers/footers

**Libraries:**
- **Java:** Apache PDFBox or iText
- **JavaScript:** jsPDF + html2canvas (for web UI)

---

### 2. **Excel/CSV** (Recommended for Data Analysis)
**Use Cases:**
- Data analysis in Excel
- Import into other systems
- Bulk data operations
- Spreadsheet manipulation

**Features:**
- Multiple sheets (one per phase/project)
- Formatted cells (dates, numbers, percentages)
- Charts embedded in Excel
- Filters and sorting
- Conditional formatting

**Libraries:**
- **Java:** Apache POI
- **JavaScript:** SheetJS (xlsx.js) or ExcelJS

---

### 3. **JSON** (Recommended for API/Integration)
**Use Cases:**
- API responses
- Data integration
- Programmatic access
- Backup/restore

**Features:**
- Structured data format
- Includes metadata
- Easy to parse programmatically

---

### 4. **HTML** (Recommended for Web Viewing)
**Use Cases:**
- Email reports
- Web sharing
- Print-friendly format

**Features:**
- Responsive design
- Embedded charts (using Chart.js or similar)
- Print stylesheet
- Self-contained (all assets embedded)

---

## 📊 Report Types to Export

### 1. **Phase Progress Report**
**Content:**
- Phase overview (name, project, customer, dates)
- Progress chart (time-series)
- Statistics (objects migrated, size, percentage)
- Forecast (ETA, confidence, rate)
- Data point history table
- Tape consumption (if applicable)

**Export Options:**
- Date range filter
- Include/exclude charts
- Include/exclude forecast
- Include/exclude raw data

---

### 2. **Project Summary Report**
**Content:**
- Project overview
- All phases with progress
- Aggregate statistics
- Phase comparison chart
- Timeline view

**Export Options:**
- Include all phases or selected phases
- Summary level (high-level vs detailed)

---

### 3. **Customer Portfolio Report**
**Content:**
- Customer information
- All projects summary
- Overall statistics
- Project comparison

**Export Options:**
- Date range
- Project filter
- Include inactive projects

---

### 4. **Data Point Export**
**Content:**
- Raw migration data
- All data points for a phase
- Timestamps, object counts, sizes

**Export Options:**
- Date range filter
- Include reference data points
- Format: CSV or Excel

---

### 5. **Comparative Report**
**Content:**
- Multiple phases side-by-side
- Comparison charts
- Performance metrics

**Export Options:**
- Select phases to compare
- Metrics to include

---

### 6. **Forecast Report**
**Content:**
- Forecast calculations
- Confidence intervals
- ETA predictions
- Rate analysis

---

## 🛠️ Implementation Design

### Backend API Endpoints

```java
// REST API Endpoints
GET  /api/reports/phase/{phaseId}/export
POST /api/reports/phase/{phaseId}/export
GET  /api/reports/project/{projectId}/export
POST /api/reports/customer/{customerId}/export
GET  /api/reports/data-points/export
POST /api/reports/custom/export

// Query Parameters
?format=pdf|csv|excel|json|html
&dateFrom=YYYY-MM-DD
&dateTo=YYYY-MM-DD
&includeCharts=true|false
&includeForecast=true|false
&includeRawData=true|false
```

### Request Body (for POST)
```json
{
  "format": "pdf",
  "reportType": "phase-progress",
  "phaseId": "uuid",
  "options": {
    "dateFrom": "2025-01-01",
    "dateTo": "2025-12-31",
    "includeCharts": true,
    "includeForecast": true,
    "includeRawData": false,
    "template": "executive" // executive, detailed, minimal
  }
}
```

### Response
```json
{
  "exportId": "uuid",
  "status": "processing" | "completed" | "failed",
  "downloadUrl": "/api/exports/{exportId}/download",
  "expiresAt": "2025-01-20T12:00:00Z",
  "format": "pdf",
  "size": 1024000
}
```

---

## 💻 CLI Integration

### New Commands

```bash
# Export phase report
migration_tracker export-report \
  --project DeepDive \
  --phase logs-archive \
  --format pdf \
  --output ./reports/logs-archive-2025-01-15.pdf

# Export with options
migration_tracker export-report \
  --project DeepDive \
  --phase logs-archive \
  --format excel \
  --include-charts \
  --date-from 2025-01-01 \
  --date-to 2025-12-31 \
  --output ./reports/report.xlsx

# Export project summary
migration_tracker export-project \
  --project DeepDive \
  --format pdf \
  --output ./reports/project-summary.pdf

# Export customer portfolio
migration_tracker export-customer \
  --customer BigDataCo \
  --format excel \
  --output ./reports/customer-portfolio.xlsx

# Export raw data
migration_tracker export-data \
  --project DeepDive \
  --phase logs-archive \
  --format csv \
  --output ./reports/raw-data.csv
```

---

## 🎨 Web UI Integration

### Export Button Locations

1. **Phase Progress Page**
   ```
   [Export Report ▼]
   ├─ Export as PDF
   ├─ Export as Excel
   ├─ Export as CSV
   └─ Export as JSON
   ```

2. **Project Summary Page**
   ```
   [Export Project Report ▼]
   ├─ Summary Report (PDF)
   ├─ Detailed Report (Excel)
   └─ Raw Data (CSV)
   ```

3. **Dashboard**
   ```
   [Export Dashboard ▼]
   ├─ Current View (PDF)
   └─ All Data (Excel)
   ```

### Export Dialog

```
┌─────────────────────────────────────────────────────────┐
│  Export Report                                          │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  Report Type: Phase Progress Report                     │
│  Phase: logs-archive                                    │
│                                                          │
│  Format:                                                │
│  ○ PDF    ○ Excel    ○ CSV    ○ JSON    ○ HTML        │
│                                                          │
│  Options:                                               │
│  ☑ Include Charts                                       │
│  ☑ Include Forecast                                     │
│  ☐ Include Raw Data                                     │
│  ☐ Include Reference Data Points                        │
│                                                          │
│  Date Range:                                            │
│  From: [2025-01-01]  To: [2025-12-31]                   │
│                                                          │
│  Template: [Executive ▼]                               │
│  • Executive (summary)                                  │
│  • Detailed (full data)                                 │
│  • Minimal (data only)                                   │
│                                                          │
│  [Cancel]  [Export]                                    │
└─────────────────────────────────────────────────────────┘
```

### Export Status

```
┌─────────────────────────────────────────────────────────┐
│  Exporting Report...                                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ████████████████████ 85%                              │
│                                                          │
│  Generating PDF...                                      │
│  Adding charts...                                       │
│                                                          │
│  [Cancel]                                               │
└─────────────────────────────────────────────────────────┘

// After completion:
┌─────────────────────────────────────────────────────────┐
│  Export Complete                                        │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ✅ Report generated successfully                       │
│                                                          │
│  File: logs-archive-report-2025-01-15.pdf              │
│  Size: 2.3 MB                                           │
│                                                          │
│  [Download] [Email] [Save to Library]                   │
└─────────────────────────────────────────────────────────┘
```

---

## 📦 Java Implementation

### Service Layer

```java
public interface ReportExportService {
    ExportResult exportPhaseReport(String phaseId, ExportOptions options);
    ExportResult exportProjectReport(String projectId, ExportOptions options);
    ExportResult exportCustomerReport(String customerId, ExportOptions options);
    ExportResult exportDataPoints(String phaseId, DataPointExportOptions options);
    byte[] generatePDF(ReportData data, PDFTemplate template);
    byte[] generateExcel(ReportData data);
    byte[] generateCSV(ReportData data);
    byte[] generateJSON(ReportData data);
    byte[] generateHTML(ReportData data);
}
```

### Export Options

```java
public class ExportOptions {
    private ExportFormat format; // PDF, EXCEL, CSV, JSON, HTML
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private boolean includeCharts;
    private boolean includeForecast;
    private boolean includeRawData;
    private boolean includeReferenceData;
    private ReportTemplate template; // EXECUTIVE, DETAILED, MINIMAL
    private String outputPath; // For CLI
}
```

### PDF Generation Example

```java
@Service
public class PDFReportGenerator {
    
    public byte[] generatePhaseReport(PhaseReportData data, ExportOptions options) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            
            try (PDPageContentStream contentStream = 
                 new PDPageContentStream(document, page)) {
                
                // Header
                drawHeader(contentStream, data.getPhaseName());
                
                // Summary Section
                drawSummary(contentStream, data.getSummary());
                
                // Chart (if enabled)
                if (options.isIncludeCharts()) {
                    drawChart(contentStream, data.getChart());
                }
                
                // Data Table
                drawDataTable(contentStream, data.getDataPoints());
                
                // Forecast (if enabled)
                if (options.isIncludeForecast()) {
                    drawForecast(contentStream, data.getForecast());
                }
                
                // Footer
                drawFooter(contentStream);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
```

### Excel Generation Example

```java
@Service
public class ExcelReportGenerator {
    
    public byte[] generatePhaseReport(PhaseReportData data, ExportOptions options) {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Summary Sheet
            Sheet summarySheet = workbook.createSheet("Summary");
            createSummarySheet(summarySheet, data);
            
            // Progress Sheet
            Sheet progressSheet = workbook.createSheet("Progress");
            createProgressSheet(progressSheet, data);
            
            // Data Points Sheet
            if (options.isIncludeRawData()) {
                Sheet dataSheet = workbook.createSheet("Data Points");
                createDataPointsSheet(dataSheet, data);
            }
            
            // Charts Sheet (if enabled)
            if (options.isIncludeCharts()) {
                Sheet chartsSheet = workbook.createSheet("Charts");
                createChartsSheet(chartsSheet, data);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }
}
```

---

## 🌐 Frontend Implementation

### React Component

```typescript
// ExportButton.tsx
import { useState } from 'react';
import { exportReport } from '../api/reports';

interface ExportButtonProps {
  phaseId: string;
  projectId: string;
}

export function ExportButton({ phaseId, projectId }: ExportButtonProps) {
  const [isExporting, setIsExporting] = useState(false);
  const [showDialog, setShowDialog] = useState(false);

  const handleExport = async (format: string, options: ExportOptions) => {
    setIsExporting(true);
    try {
      const result = await exportReport({
        phaseId,
        format,
        ...options
      });
      
      // Download file
      const blob = new Blob([result.data], { 
        type: getMimeType(format) 
      });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = result.filename;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Export failed:', error);
    } finally {
      setIsExporting(false);
      setShowDialog(false);
    }
  };

  return (
    <>
      <button onClick={() => setShowDialog(true)}>
        Export Report
      </button>
      
      {showDialog && (
        <ExportDialog
          onExport={handleExport}
          onClose={() => setShowDialog(false)}
        />
      )}
    </>
  );
}
```

### API Client

```typescript
// api/reports.ts
export async function exportReport(params: {
  phaseId: string;
  format: 'pdf' | 'excel' | 'csv' | 'json' | 'html';
  options: ExportOptions;
}): Promise<Blob> {
  const response = await fetch('/api/reports/phase/export', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(params),
  });
  
  if (!response.ok) {
    throw new Error('Export failed');
  }
  
  return response.blob();
}
```

---

## 📧 Email Export

### Scheduled Reports

```java
@Service
public class ScheduledReportService {
    
    @Scheduled(cron = "0 0 9 * * MON") // Every Monday at 9 AM
    public void sendWeeklyReports() {
        List<Customer> customers = customerService.findAllActive();
        
        for (Customer customer : customers) {
            ExportResult report = exportService.exportCustomerReport(
                customer.getId(),
                ExportOptions.builder()
                    .format(ExportFormat.PDF)
                    .template(ReportTemplate.EXECUTIVE)
                    .build()
            );
            
            emailService.sendReport(
                customer.getEmail(),
                "Weekly Migration Report",
                report
            );
        }
    }
}
```

---

## 🗂️ File Storage

### Storage Options

1. **Local File System**
   - Store in `./exports/` directory
   - Organize by date: `exports/2025/01/15/`
   - Cleanup old files (configurable retention)

2. **S3/Cloud Storage**
   - Store in S3 bucket
   - Generate signed URLs for download
   - Automatic expiration

3. **Database (for small files)**
   - Store as BLOB in database
   - Good for audit trail
   - Not recommended for large files

### File Naming Convention

```
{report-type}-{identifier}-{date}-{timestamp}.{ext}

Examples:
- phase-progress-logs-archive-2025-01-15-143022.pdf
- project-summary-deepdive-2025-01-15-143022.xlsx
- customer-portfolio-bigdataco-2025-01-15-143022.pdf
```

---

## 🔧 Configuration

### Application Properties

```yaml
export:
  default-format: pdf
  storage:
    type: local # local, s3, database
    path: ./exports
    retention-days: 30
  pdf:
    template-path: ./templates/pdf
    font-path: ./fonts
  excel:
    max-rows-per-sheet: 100000
  email:
    enabled: true
    from: migration-tracker@spectralogic.com
    smtp:
      host: smtp.example.com
      port: 587
```

---

## 📋 Implementation Checklist

### Phase 1: Basic Export (Week 1-2)
- [ ] Add export command to CLI
- [ ] Implement CSV export
- [ ] Implement JSON export
- [ ] Basic API endpoint

### Phase 2: PDF Export (Week 3-4)
- [ ] PDF generation library integration
- [ ] Template system
- [ ] Chart rendering in PDF
- [ ] Multi-page support

### Phase 3: Excel Export (Week 5-6)
- [ ] Excel generation
- [ ] Multiple sheets
- [ ] Formatting and styling
- [ ] Embedded charts

### Phase 4: Web UI Integration (Week 7-8)
- [ ] Export button components
- [ ] Export dialog
- [ ] Progress indicator
- [ ] Download handling

### Phase 5: Advanced Features (Week 9-10)
- [ ] Scheduled exports
- [ ] Email delivery
- [ ] Export history
- [ ] Custom templates

---

## 🎨 PDF Template Examples

### Executive Template
- Clean, minimal design
- High-level summary
- Key metrics highlighted
- Professional branding

### Detailed Template
- All data included
- Multiple charts
- Full data tables
- Technical details

### Minimal Template
- Data only
- No charts
- Simple formatting
- Fast generation

---

## 📊 Example Export Outputs

### PDF Report Structure
```
Page 1: Cover
  - Title: "Migration Progress Report"
  - Phase: logs-archive
  - Date: January 15, 2025
  - Generated by: Migration Tracker v0.6.2

Page 2: Executive Summary
  - Progress: 78%
  - Objects: 521 / 668
  - ETA: January 20, 2025
  - Key Metrics

Page 3: Progress Chart
  - Time-series chart
  - Trend line
  - Forecast overlay

Page 4: Statistics
  - Migration rate
  - Size breakdown
  - Tape consumption (if applicable)

Page 5: Data Points
  - Table of all data points
  - Timestamps, counts, sizes

Page 6: Forecast Details
  - ETA calculation
  - Confidence intervals
  - Rate analysis
```

### Excel Workbook Structure
```
Sheet 1: Summary
  - Overview table
  - Key metrics

Sheet 2: Progress
  - Date | Objects | Size | Percentage
  - Chart embedded

Sheet 3: Data Points
  - All raw data
  - Filterable

Sheet 4: Forecast
  - Forecast calculations
  - Confidence data
```

---

## 🚀 Quick Start Implementation

### Minimal Viable Export (CSV)

```java
@RestController
@RequestMapping("/api/reports")
public class ReportExportController {
    
    @GetMapping("/phase/{phaseId}/export")
    public ResponseEntity<Resource> exportPhaseReport(
            @PathVariable String phaseId,
            @RequestParam(defaultValue = "csv") String format) {
        
        PhaseReportData data = reportService.getPhaseReport(phaseId);
        byte[] csv = csvGenerator.generate(data);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                   "attachment; filename=report.csv")
            .contentType(MediaType.TEXT_CSV)
            .body(new ByteArrayResource(csv));
    }
}
```

---

## 📝 Next Steps

1. **Choose export formats** (start with CSV, add PDF/Excel)
2. **Design report templates** (executive, detailed, minimal)
3. **Implement backend services** (PDF/Excel generators)
4. **Add CLI commands** (export-report, export-project)
5. **Build web UI components** (export buttons, dialogs)
6. **Add email functionality** (scheduled reports)
7. **Implement file storage** (local or S3)
8. **Add export history** (track all exports)

Would you like me to:
- Create the Java service implementations?
- Build the React export components?
- Design the PDF templates?
- Set up the CLI export commands?
