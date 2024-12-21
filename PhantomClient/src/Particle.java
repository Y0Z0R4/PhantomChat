import java.awt.Color;
import java.util.Random;

public class Particle {
    // Position of the star
    int x, y;
    // Size of the star
    int size;
    // Opacity (transparency)
    float opacity;
    // Color of the star
    Color color;
    // Speed of movement
    int speedX, speedY;

    private static final Random rand = new Random();

    public Particle() {
        // Randomize the star's position, size, and color
        this.x = rand.nextInt(800); // Random x position within screen width
        this.y = rand.nextInt(600); // Random y position within screen height
        this.size = rand.nextInt(2) + 1; // Star size between 1 and 2 pixels
        this.opacity = rand.nextFloat() * 0.7f + 0.3f; // Random opacity between 0.3 and 1
        this.color = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));

        // Random speed for the stars
        this.speedX = rand.nextInt(3) - 1; // Random speed between -1 and 1 for horizontal movement
        this.speedY = rand.nextInt(3) - 1; // Random speed between -1 and 1 for vertical movement
    }

    // Update the position of the star
    public void update() {
        this.x += speedX;
        this.y += speedY;

        // Keep the stars within the screen bounds by resetting their position when they move off-screen
        if (this.x < 0 || this.x > 800) {
            this.x = rand.nextInt(800);
        }
        if (this.y < 0 || this.y > 600) {
            this.y = rand.nextInt(600);
        }

        // Decrease opacity gradually to simulate fading stars
        this.opacity -= 0.005f;
        if (this.opacity < 0) {
            this.opacity = 0;
        }
    }
}
