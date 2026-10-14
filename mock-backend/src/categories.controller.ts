import { Controller, Get, NotFoundException, Param } from '@nestjs/common';

const categories = [
  {
    id: 'sales',
    name: 'Sales',
    description: 'Revenue, pipeline and customer performance.',
  },
  {
    id: 'finance',
    name: 'Finance',
    description: 'Financial statements, cash and receivables.',
  },
  {
    id: 'operations',
    name: 'Operations',
    description: 'Inventory, fulfilment and logistics.',
  },
  {
    id: 'hr',
    name: 'HR',
    description: 'Workforce, headcount and leave.',
  },
  {
    id: 'dev',
    name: 'Development',
    description: 'Development resources.',
  },
];

const subCategories: Record<string, unknown[]> = {
  sales: [
    {
      id: 'sales-performance',
      categoryId: 'sales',
      name: 'Sales Performance',
      description: 'How sales are tracking over time and by region.',
      reports: [
        {
          id: 'monthly-sales',
          name: 'Monthly Sales',
          description: 'Sales totals by month for the selected period and regions.',
          reportConfigId: 'monthly-sales',
        },
        {
          id: 'sales-by-region',
          name: 'Sales by Region',
          description: 'Compare sales across regions for a single period.',
          reportConfigId: 'sales-by-region',
        },
        {
          id: 'sales-trends',
          name: 'Sales Trends',
          description: 'Rolling trend lines for product lines over time.',
          reportConfigId: 'sales-trends',
        },
      ],
    },
    {
      id: 'customer-analysis',
      categoryId: 'sales',
      name: 'Customer Analysis',
      description: 'Acquisition and retention of customers.',
      reports: [
        {
          id: 'customer-growth',
          name: 'Customer Growth',
          description: 'New and active customers per period.',
          reportConfigId: 'customer-growth',
        },
        {
          id: 'customer-retention',
          name: 'Customer Retention',
          description: 'Cohort retention for customers acquired in a period.',
          reportConfigId: 'customer-retention',
        },
      ],
    },
  ],
  finance: [
    {
      id: 'financial-statements',
      categoryId: 'finance',
      name: 'Financial Statements',
      description: 'Core statements for a reporting period.',
      reports: [
        {
          id: 'profit-and-loss',
          name: 'Profit and Loss',
          description: 'Income statement for a period by cost centre.',
          reportConfigId: 'profit-and-loss',
        },
        {
          id: 'cash-flow',
          name: 'Cash Flow',
          description: 'Cash inflows and outflows as at a date.',
          reportConfigId: 'cash-flow',
        },
      ],
    },
    {
      id: 'receivables',
      categoryId: 'finance',
      name: 'Receivables',
      description: 'Outstanding customer balances.',
      reports: [
        {
          id: 'accounts-receivable-aging',
          name: 'Accounts Receivable Aging',
          description: 'Outstanding invoices grouped by age.',
          reportConfigId: 'accounts-receivable-aging',
        },
      ],
    },
  ],
  operations: [
    {
      id: 'inventory',
      categoryId: 'operations',
      name: 'Inventory',
      description: 'Stock on hand and movements.',
      reports: [
        {
          id: 'inventory-levels',
          name: 'Inventory Levels',
          description: 'Stock on hand by warehouse and SKU.',
          reportConfigId: 'inventory-levels',
        },
      ],
    },
    {
      id: 'fulfilment',
      categoryId: 'operations',
      name: 'Fulfilment',
      description: 'Order processing performance.',
      reports: [
        {
          id: 'order-fulfilment',
          name: 'Order Fulfilment',
          description: 'Order cycle times and on-time delivery rates.',
          reportConfigId: 'order-fulfilment',
        },
      ],
    },
  ],
  hr: [
    {
      id: 'workforce',
      categoryId: 'hr',
      name: 'Workforce',
      description: 'Headcount and leave across the organisation.',
      reports: [
        {
          id: 'headcount',
          name: 'Headcount',
          description: 'Employee headcount by department as at a date.',
          reportConfigId: 'headcount',
        },
        {
          id: 'leave-balances',
          name: 'Leave Balances',
          description: 'Accrued and taken leave by employee.',
          reportConfigId: 'leave-balances',
        },
      ],
    },
  ],
  dev: [
    {
      id: 'javaDevs',
      categoryId: 'dev',
      name: 'Java Developers',
      description: 'Headcount of java developers across the organisation.',
      reports: [
        {
          id: 'headcount',
          name: 'Headcount',
          description: 'Java Developers headcount by department as at a date.',
          reportConfigId: 'headcount',
        },
        {
          id: 'leave-balances',
          name: 'Leave Balances',
          description: 'Accrued and taken leave by employee.',
          reportConfigId: 'leave-balances',
        },
      ],
    },
  ],
};

@Controller('categories')
export class CategoriesController {
  @Get()
  categories() {
    return categories;
  }

  @Get(':id')
  category(@Param('id') id: string) {
    const category = categories.find((c) => c.id === id);
    if (!category) throw new NotFoundException();
    return category;
  }

  @Get(':id/subcategories')
  subCategories(@Param('id') id: string) {
    if (!Object.hasOwn(subCategories, id)) throw new NotFoundException();
    return subCategories[id];
  }
}
