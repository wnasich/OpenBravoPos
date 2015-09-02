SET @date_from = '2000-01-01 00:00:00';
SET @date_to = '2014-12-31 23:59:59';

select @date_from, @date_to;
delete from CLOSEDCASH where CLOSEDCASH.MONEY in (select RECEIPTS.MONEY from RECEIPTS where RECEIPTS.DATENEW between @date_from and @date_to);
select ROW_COUNT() as closedcash_deleted;
delete from PAYMENTS where PAYMENTS.RECEIPT in (select RECEIPTS.ID from RECEIPTS where RECEIPTS.DATENEW between @date_from and @date_to);
select ROW_COUNT() as payments_deleted;
delete from TAXLINES where TAXLINES.RECEIPT in (select RECEIPTS.ID from RECEIPTS where RECEIPTS.DATENEW between @date_from and @date_to);
select ROW_COUNT() as taxlines_deleted;
delete from TICKETLINES where TICKETLINES.TICKET in (select TICKETS.ID from TICKETS inner join RECEIPTS ON TICKETS.ID=RECEIPTS.ID where RECEIPTS.DATENEW between @date_from and @date_to);
select ROW_COUNT() as ticketlines_deleted;
delete from STOCKDIARY where DATENEW between @date_from and @date_to;
select ROW_COUNT() as stockdiary_deleted;
delete from TICKETS where TICKETS.ID in (select RECEIPTS.ID from RECEIPTS where RECEIPTS.DATENEW between @date_from and @date_to);
select ROW_COUNT() as tickets_deleted;
delete from RECEIPTS where DATENEW between @date_from and @date_to;
select ROW_COUNT() as receipts_deleted;
