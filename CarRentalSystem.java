package com.car.rental;

import java.sql.*;
import java.util.Scanner;
import com.dbutil.DBConnection;
import java.time.LocalDate;

public class CarRentalSystem {
    private Scanner scanner = new Scanner(System.in);

    public void start() {
        while (true) {
            displayMenu();
            int choice = scanner.nextInt();
            scanner.nextLine(); // Clear buffer
            
            switch (choice) {
                case 1:
                    rentCar();
                    break;
                case 2:
                    returnCar();
                    break;
                case 3:
                    System.out.println("Thank you for using Car Rental System. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private void displayMenu() {
        System.out.println("\n=== Car Rental System ===");
        System.out.println("1. Rent a Car");
        System.out.println("2. Return a Car");
        System.out.println("3. Exit");
        System.out.print("Enter your choice: ");
    }

    private void displayAvailableCars() {
        String sql = "SELECT * FROM cars WHERE is_available = true";
        
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            System.out.println("\nAvailable Cars for Rent:");
            System.out.println("----------------------------------------");
            System.out.printf("%-5s %-15s %-15s %-10s%n", "ID", "Brand", "Model", "Rate/Day");
            System.out.println("----------------------------------------");
            
            while (rs.next()) {
                System.out.printf("%-5d %-15s %-15s $%-9.2f%n",
                    rs.getInt("car_id"),
                    rs.getString("brand"),
                    rs.getString("model"),
                    rs.getDouble("rate_per_day"));
            }
            System.out.println("----------------------------------------");
            
        } catch (SQLException e) {
            System.out.println("Error displaying cars: " + e.getMessage());
        }
    }


    private void rentCar() {
        // First display available cars
        displayAvailableCars();
        
        // Get rental information
        System.out.print("\nEnter Car ID to rent: ");
        int carId = scanner.nextInt();
        scanner.nextLine(); // Clear buffer
        
        System.out.print("Enter your name: ");
        String customerName = scanner.nextLine();
        
        System.out.print("Enter number of days for rental: ");
        int days = scanner.nextInt();
        scanner.nextLine(); // Clear buffer
        
        // Calculate dates
        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(days);
        
        // First get car details to calculate cost
        try (Connection conn = DBConnection.getConnection()) {
            // Check if car exists and is available
            String checkSql = "SELECT brand, model, rate_per_day FROM cars WHERE car_id = ? AND is_available = true";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, carId);
                ResultSet rs = checkStmt.executeQuery();
                
                if (!rs.next()) {
                    System.out.println("Invalid car ID or car is not available!");
                    return;
                }
                
                String carBrand = rs.getString("brand");
                String carModel = rs.getString("model");
                double ratePerDay = rs.getDouble("rate_per_day");
                double totalCost = ratePerDay * days;
                
                // Display rental information before confirmation
                System.out.println("\nRental Information:");
                System.out.println("----------------------------------------");
                System.out.println("Customer Name: " + customerName);
                System.out.println("Car: " + carBrand + " " + carModel);
                System.out.println("Rental Days: " + days);
                System.out.println("Start Date: " + startDate);
                System.out.println("End Date: " + endDate);
                System.out.printf("Total Cost: $%.2f%n", totalCost);
                System.out.println("----------------------------------------");
                
                // Get confirmation from user
                System.out.print("\nDo you want to proceed with the rental? (yes/no): ");
                String confirmation = scanner.nextLine().trim().toLowerCase();
                
                if (!confirmation.equals("yes")) {
                    System.out.println("\nRental process cancelled. Thank you for considering our service!");
                    return;
                }
                
                // Create customer and get ID
                int customerId = createCustomer(customerName);
                if (customerId == -1) {
                    System.out.println("Error creating customer record.");
                    return;
                }
                
                // Create rental record
                String rentSql = "INSERT INTO rentals (car_id, customer_id, start_date, end_date, total_cost) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement rentStmt = conn.prepareStatement(rentSql)) {
                    rentStmt.setInt(1, carId);
                    rentStmt.setInt(2, customerId);
                    rentStmt.setDate(3, Date.valueOf(startDate));
                    rentStmt.setDate(4, Date.valueOf(endDate));
                    rentStmt.setDouble(5, totalCost);
                    rentStmt.executeUpdate();
                }
                
                // Update car availability
                String updateCarSql = "UPDATE cars SET is_available = false WHERE car_id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateCarSql)) {
                    updateStmt.setInt(1, carId);
                    updateStmt.executeUpdate();
                }
                
                // Display final confirmation and rental details
                System.out.println("\nRental Confirmation:");
                System.out.println("----------------------------------------");
                System.out.println("Customer Name: " + customerName);
                System.out.println("Customer ID: " + customerId);
                System.out.println("Car: " + carBrand + " " + carModel);
                System.out.println("Rental Days: " + days);
                System.out.println("Start Date: " + startDate);
                System.out.println("End Date: " + endDate);
                System.out.printf("Total Cost: $%.2f%n", totalCost);
                System.out.println("----------------------------------------");
                System.out.println("Car rented successfully!");
                System.out.println("Thank you for choosing our service. Enjoy your ride!");
            }
        } catch (SQLException e) {
            System.out.println("Error renting car: " + e.getMessage());
        }
    }

    private int createCustomer(String name) {
        String sql = "INSERT INTO customers (customer_name) VALUES (?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, name);
            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.out.println("Error creating customer: " + e.getMessage());
        }
        return -1;
    }

    private void returnCar() {
        System.out.print("Enter Car ID to return: ");
        int carId = scanner.nextInt();
        
        System.out.print("Enter Customer ID: ");
        int customerId = scanner.nextInt();
        
        String returnSql = "UPDATE rentals SET return_date = CURRENT_DATE " +
                          "WHERE car_id = ? AND customer_id = ? AND return_date IS NULL";
        String updateCarSql = "UPDATE cars SET is_available = true WHERE car_id = ?";
        
        try (Connection conn = DBConnection.getConnection()) {
            // First verify the rental exists
            String checkSql = "SELECT r.rental_id, c.customer_name, ca.brand, ca.model " +
                            "FROM rentals r " +
                            "JOIN customers c ON r.customer_id = c.customer_id " +
                            "JOIN cars ca ON r.car_id = ca.car_id " +
                            "WHERE r.car_id = ? AND r.customer_id = ? AND r.return_date IS NULL";
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, carId);
                checkStmt.setInt(2, customerId);
                ResultSet rs = checkStmt.executeQuery();
                
                if (!rs.next()) {
                    System.out.println("No active rental found for this car and customer!");
                    return;
                }
                
                // Process return
                try (PreparedStatement returnStmt = conn.prepareStatement(returnSql)) {
                    returnStmt.setInt(1, carId);
                    returnStmt.setInt(2, customerId);
                    returnStmt.executeUpdate();
                }
                
                try (PreparedStatement updateStmt = conn.prepareStatement(updateCarSql)) {
                    updateStmt.setInt(1, carId);
                    updateStmt.executeUpdate();
                }
                
                System.out.println("\nCar returned successfully!");
                System.out.println("Customer Name: " + rs.getString("customer_name"));
                System.out.println("Car: " + rs.getString("brand") + " " + rs.getString("model"));
            }
            
        } catch (SQLException e) {
            System.out.println("Error returning car: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        CarRentalSystem cr = new CarRentalSystem();
        cr.start();
    }
}