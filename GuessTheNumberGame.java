import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GuessTheNumberGame extends JFrame {
    private int secretNumber;
    private int attempts;
    private int score;

    private JLabel messageLabel;
    private JTextField guessTextField;
    private JButton submitButton;

    public GuessTheNumberGame() {
        secretNumber = generateRandomNumber(1, 100);
        attempts = 0;
        score = 100;

        setTitle("Guess the Number Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout());

        messageLabel = new JLabel("I'm thinking of a number between 1 and 100. Can you guess it?");
        add(messageLabel);

        guessTextField = new JTextField(10);
        add(guessTextField);

        submitButton = new JButton("Submit");
        submitButton.addActionListener(new SubmitButtonListener());
        add(submitButton);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private class SubmitButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            try {
                int guess = Integer.parseInt(guessTextField.getText());
                attempts++;

                if (guess < secretNumber) {
                    messageLabel.setText("Too low!");
                    score -= 10;
                } else if (guess > secretNumber) {
                    messageLabel.setText("Too high!");
                    score -= 10;
                } else {
                    messageLabel.setText("Congratulations! You guessed the number correctly.");
                    messageLabel.setText(messageLabel.getText() + " Number of attempts: " + attempts);
                    messageLabel.setText(messageLabel.getText() + " Your score: " + score);
                    guessTextField.setEnabled(false);
                    submitButton.setEnabled(false);
                }
            } catch (NumberFormatException ex) {
                messageLabel.setText("Invalid input. Please enter a number.");
            }

            guessTextField.setText("");
            guessTextField.requestFocus();
        }
    }

    private int generateRandomNumber(int min, int max) {
        return (int) (Math.random() * (max - min + 1) + min);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new GuessTheNumberGame();
            }
        });
    }
}
