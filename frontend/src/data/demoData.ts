import type { DocumentItem, NavigationItem } from "../types/models";

export const documents: DocumentItem[] = [
    { title: "Procurement Policy v2.1", type: "Policy", date: "Apr 12, 2024", color: "blue", text: "Software purchases over $5,000 require a purchase request, budget validation, manager approval, and Procurement review. A purchase order must be issued before ordering." },
    { title: "Finance Approval Matrix", type: "Spreadsheet", date: "Mar 28, 2024", color: "green", text: "Finance reviews available budget and cost appropriateness. Purchases over $20,000 also require final approval from the CFO or delegated authority." },
    { title: "IT Purchasing Guidelines", type: "Guideline", date: "Feb 15, 2024", color: "purple", text: "IT Operations reviews software for security, licensing, and integration requirements before Procurement finalizes vendor terms." },
    { title: "Software Expense Policy", type: "Policy", date: "Jan 10, 2024", color: "blue", text: "Software costs are recorded against the Software Budget cost center. Include the business justification, vendor details, and total cost with each request." },
    { title: "Vendor Management Policy", type: "Policy", date: "Nov 5, 2023", color: "blue", text: "Procurement reviews vendor terms and contract requirements. Approved orders are sent to the vendor by the Procurement team." },
];

export const entities = ["Finance Team", "Procurement", "IT Operations", "Software Budget"];

export const approvalSteps = [
    ["Request Initiation", "The requester submits a purchase request in the Procurement system, with business justification, vendor details, and total cost."],
    ["Budget Check", "Finance validates that sufficient budget exists in the Software Budget category."],
    ["Manager Approval", "The requester's manager approves the request."],
    ["Finance Review", "The Finance Team reviews the request for policy compliance and cost appropriateness."],
    ["IT Operations Approval", "IT Operations verifies the software meets security, licensing, and integration requirements."],
    ["Procurement Approval", "Procurement reviews vendor terms and contract requirements."],
    ["Final Approval", "The CFO or delegated authority provides final approval for purchases over $20,000."],
    ["Purchase Order Issued", "Upon all approvals, Procurement issues the purchase order to the vendor."],
] as const;

export const sampleQuestion = "What is the expense approval process for software purchases over $5,000?";

export const navigation: NavigationItem[] = [
    { label: "Dashboard", path: "/dashboard", icon: "home" },
    { label: "Ask AI", path: "/ask-ai", icon: "spark" },
    { label: "Knowledge Base", path: "/knowledge", icon: "book" },
    { label: "Entities", path: "/entities", icon: "users" },
    { label: "Uploads", path: "/uploads", icon: "upload" },
    { label: "Usage", path: "/usage", icon: "chart" },
    { label: "Admin", path: "/admin", icon: "shield" },
];

export const summaryMetrics = [
    ["Knowledge Items", "12,842", "↑ 3.2%"],
    ["Queries Today", "248", "↑ 18.6%"],
    ["Estimated AI Cost", "$1.48", "↓ 6.1%"],
    ["Cache Hit Rate", "82%", "↑ 5.4%"],
] as const;

export const uploadStatuses = [
    ["Q2 Vendor Contracts.md", "Pending"],
    ["IT Security Standards.md", "Processing"],
    ["Finance Q1 Reports.md", "Completed"],
    ["Legacy HR Guidelines.md", "Failed"],
] as const;
