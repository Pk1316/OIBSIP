import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

class Transaction {
    private Date date;
    private double amount;
    private String type;

    public Transaction(double amount, String type) {
        this.date = new Date();
        this.amount = amount;
        this.type = type;
    }

    public Date getDate() {
        return date;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }
}

class Account {
    private String accountNumber;
    private String password;
    private double balance;
    private List<Transaction> transactionHistory;

    public Account(String accountNumber, String password) {
        this.accountNumber = accountNumber;
        this.password = password;
        this.balance = 0;
        this.transactionHistory = new ArrayList<>();
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public boolean validatePassword(String password) {
        return this.password.equals(password);
    }

    public double getBalance() {
        return balance;
    }

    public void deposit(double amount) { //credit
        balance += amount;
        Transaction transaction = new Transaction(amount, "DEPOSIT");
        transactionHistory.add(transaction);
    }

    public void withdraw(double amount) { //debit
        if (balance >= amount) {
            balance -= amount;
            Transaction transaction = new Transaction(amount, "WITHDRAWAL");
            transactionHistory.add(transaction);
        } else {
            System.out.println("Insufficient balance.");
        }
    }

    public void transfer(Account destinationAccount, double amount) { //transfering one account to another
        if (balance >= amount) {
            balance -= amount;
            destinationAccount.deposit(amount);
            Transaction transaction = new Transaction(amount, "TRANSFER");
            transactionHistory.add(transaction);
            System.out.println("Transfer successful.");
            System.out.println("Current balance: $" + balance);
        } else {
            System.out.println("Insufficient balance for transfer.");
        }
    }

    public List<Transaction> getTransactionHistory() { //transaction history
        return transactionHistory;
    }
}

public class ATMInterface {
    public static void main(String[] args) {
        Account account1 = new Account("123456789", "password1");
        Account account2 = new Account("987654321", "password2");
        Scanner scanner = new Scanner(System.in);
        int choice;
        String accountNumber, password;

        System.out.print("Enter your account number: ");
        accountNumber = scanner.nextLine();
        System.out.print("Enter your password: ");
        password = scanner.nextLine();

        if (!account1.getAccountNumber().equals(accountNumber) || !account1.validatePassword(password)) {
            System.out.println("Invalid account number or password. Exiting ATM.");
            return;
        }

        do {
            System.out.println("ATM Interface");
            System.out.println("1. Deposit");
            System.out.println("2. Withdraw");
            System.out.println("3. Transfer");
            System.out.println("4. Transaction History");
            System.out.println("5. Exit");
            System.out.print("Enter your choice: ");
            choice = scanner.nextInt();

            switch (choice) { //menu in atm interface
                case 1:
                    System.out.print("Enter the amount to deposit: ");
                    double depositAmount = scanner.nextDouble();
                    account1.deposit(depositAmount);
                    System.out.println("Deposit successful.");
                    System.out.println("Current balance: $" + account1.getBalance());
                    break;
                case 2:
                    System.out.print("Enter the amount to withdraw: ");
                    double withdrawalAmount = scanner.nextDouble();
                    account1.withdraw(withdrawalAmount);
                    System.out.println("Withdrawal successful.");
                    System.out.println("Current balance: $" + account1.getBalance());
                    break;
                case 3:
                    System.out.print("Enter the amount to transfer: ");
                    double transferAmount = scanner.nextDouble();
                    System.out.print("Enter the destination account number: ");
                    String destinationAccountNumber = scanner.next();
                    Account destinationAccount = account2;  // Replace with account lookup based on destinationAccountNumber
                    account1.transfer(destinationAccount, transferAmount);
                    break;
                case 4:
                    System.out.println("Transaction History:");
                    List<Transaction> transactions = account1.getTransactionHistory();
                    for (Transaction transaction : transactions) {
                        System.out.println("Date: " + transaction.getDate());
                        System.out.println("Amount: $" + transaction.getAmount());
                        System.out.println("Type: " + transaction.getType());
                        System.out.println("-----------------------------");
                    }
                    break;
                case 5:
                    System.out.println("Exiting ATM. Thank you!");
                    break;
                default:
                    System.out.println("Invalid choice. Please try again.");
                    break;
            }
            System.out.println();
        } while (choice != 5);

        scanner.close();
    }
}
