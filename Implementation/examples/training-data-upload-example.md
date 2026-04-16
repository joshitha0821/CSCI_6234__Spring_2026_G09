# Training Data Upload Example

Use this file as a starter document for `/api/training/documents`.
It mixes business glossary, schema notes, and approved NL-to-SQL examples.

## Business Glossary

- `revenue`: `SUM(sales.amount)`
- `order volume`: `SUM(sales.quantity)`
- `last month`: from first day of previous month to last day of previous month
- `low stock`: `inventory.stock < inventory.reorder_level`

## Schema Notes

- `sales(order_date, region, customer, product, amount, quantity)`
- `inventory(product, category, stock, reorder_level)`
- Region values are `North`, `South`, `East`, `West`

## Approved NL -> SQL Examples

### Question
What were last month sales by region?

### SQL
```sql
SELECT region, SUM(amount) AS total_sales
FROM sales
WHERE order_date >= DATEADD('MONTH', -1, CURRENT_DATE())
GROUP BY region
ORDER BY total_sales DESC
LIMIT 20;
```

---

### Question
Show top 10 customers by revenue.

### SQL
```sql
SELECT customer, SUM(amount) AS total_sales
FROM sales
GROUP BY customer
ORDER BY total_sales DESC
LIMIT 10;
```

---

### Question
Which products are below reorder level?

### SQL
```sql
SELECT product, stock, reorder_level
FROM inventory
WHERE stock < reorder_level
ORDER BY stock ASC
LIMIT 50;
```

## Governance Notes

- Only read-only SQL is allowed (SELECT/CTE).
- Avoid personally identifiable information in documents.
- Keep examples versioned and reviewed before upload.
