package com.relaybook.syllabus;

import java.util.Scanner;

/** Menu-driven console exercise; Scanner is used for all keyboard input. */
public final class BankAccountDemo {
    private BankAccountDemo() { }

    public static void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Bank Account syllabus exercise ===");
        String name = readNonBlank(scanner, "Account holder name: ");
        double openingBalance = readAmount(scanner, "Opening balance: ", true);
        BankAccount account = new BankAccount("STUDENT-001", name, openingBalance);

        boolean running = true;
        while (running) {
            System.out.println("\n1 Deposit  2 Withdraw  3 Change name  4 Charge fee  5 Summary  0 Exit");
            System.out.print("Choose: ");
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    showResult("Deposit", account.deposit(readAmount(scanner, "Amount: ", false)));
                    break;
                case "2":
                    showResult("Withdrawal", account.withdraw(readAmount(scanner, "Amount: ", false)));
                    break;
                case "3":
                    showResult("Name change", account.changeName(readNonBlank(scanner, "New name: ")));
                    break;
                case "4":
                    showResult("Fee charge", account.chargeFee(readAmount(scanner, "Fee: ", false)));
                    break;
                case "5":
                    System.out.println(account.toString());
                    break;
                case "0":
                    running = false;
                    break;
                default:
                    System.out.println("Choose one of the listed menu options.");
            }
        }
        System.out.println("Bank account demo finished.");
    }

    private static void showResult(String action, boolean success) {
        System.out.println(success ? action + " successful." : action + " failed; check amount or available balance.");
    }

    private static String readNonBlank(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("This field cannot be blank.");
        }
    }

    private static double readAmount(Scanner scanner, String prompt, boolean allowZero) {
        while (true) {
            System.out.print(prompt);
            try {
                double amount = Double.parseDouble(scanner.nextLine().trim());
                if (amount >= 0 && (allowZero || amount > 0) && Double.isFinite(amount)) {
                    return amount;
                }
            } catch (NumberFormatException ignored) {
                // Explain and re-prompt instead of terminating the Scanner demo.
            }
            System.out.println(allowZero ? "Enter a finite non-negative number." : "Enter a finite amount greater than zero.");
        }
    }
}
