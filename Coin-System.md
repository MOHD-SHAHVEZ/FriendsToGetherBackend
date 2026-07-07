=> This File is for to complete coin system allocation

CoinPlanController
→ User-facing APIs
GET /coin-plans
GET /coin-plans/{id} (optional)

AdminCoinPlanController
→ Admin-only APIs
POST /admin/coin-plans
PUT /admin/coin-plans/{id}
PATCH /admin/coin-plans/{id}/status
GET /admin/coin-plans

===================================
USER API'S 
===================================
GET    /coin-plans

GET    /wallet

GET    /wallet/transactions

POST   /orders

POST   /gifts/send

GET    /gifts/history

=================================
ONLY ADMIN API'S
=================================
POST   /admin/coin-plans

PUT    /admin/coin-plans/{id}

PATCH  /admin/coin-plans/{id}/status

GET    /admin/orders

GET    /admin/payments

GET    /admin/refunds

POST   /admin/wallet/add-coins

=======================================
PAYMENT API'S 
=======================================
POST   /payments/verify

POST   /webhooks/payment

======================================

Admin CoinPlan CRUD APIs → User CoinPlan APIs → UserWallet Module → WalletTransaction Module 
→ Admin Coin Management (Testing) → Gift Module → CoinOrder → Payment → Webhook → Refund.

1. CoinPlan Module
   ↓
2. UserWallet Module
   ↓
3. WalletTransaction Module
   ↓
4. Gift Module
   ↓
5. CoinOrder Module
   ↓
6. Payment Module
   ↓
7. Webhook Module
   ↓
8. Refund Module