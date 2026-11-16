package com.relaybook.syllabus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Stand-alone class for the Bank Account exercise in the Java syllabus. */
public final class BankAccount {
    private final String accountNumber;
    private String accountHolderName;
    private BigDecimal balance;

    public BankAccount(String accountNumber, String accountHolderName, double openingBalance) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Account number is required.");
        }
        if (accountHolderName == null || accountHolderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Account holder name is required.");
        }
        if (openingBalance < 0 || Double.isNaN(openingBalance) || Double.isInfinite(openingBalance)) {
            throw new IllegalArgumentException("Opening balance must be a finite, non-negative amount.");
        }
        this.accountNumber = accountNumber.trim();
        this.accountHolderName = accountHolderName.trim();
        this.balance = BigDecimal.valueOf(openingBalance).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean deposit(double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        balance = balance.add(BigDecimal.valueOf(amount)).setScale(2, RoundingMode.HALF_UP);
        return true;
    }

    public boolean withdraw(double amount) {
        if (!isPositiveFinite(amount)) {
            return false;
        }
        BigDecimal requested = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
        if (balance.compareTo(requested) < 0) {
            return false;
        }
        balance = balance.subtract(requested);
        return true;
    }

    public boolean changeName(String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }
        this.accountHolderName = newName.trim();
        return true;
    }

    /** Charges a non-refundable account fee only when the balance can cover it. */
    public boolean chargeFee(double fee) {
        return withdraw(fee);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    private boolean isPositiveFinite(double amount) {
        return amount > 0 && !Double.isNaN(amount) && !Double.isInfinite(amount);
    }

    @Override
    public String toString() {
        return "Account summary | No: " + accountNumber
                + " | Holder: " + accountHolderName
                + " | Balance: " + balance.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
