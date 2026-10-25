package none.battleshipspro;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * BattleshipsPro.java
 * Professional Battleships game with GraalVM integration.
 */
public class BattleshipsPro extends Application {

    private static final int GRID_SIZE = 20;
    private static final int CELL_SIZE = 16; // Reduced to fit the UI better

    private Context polyglot;
    private Value jsEngine;
    private Value pythonAI;

    private final List<ShipType> shipTypes = Arrays.asList(
            new ShipType("Scout Boat", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}),
            new ShipType("Patrol Vessel", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}),
            new ShipType("Submarine", new int[][]{{0, 0}, {0, 1}, {1, 1}, {2, 1}}),
            new ShipType("Destroyer", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}}),
            new ShipType("Dreadnought", new int[][]{{2, 0}, {0, 1}, {1, 1}, {2, 1}, {2, 2}}),
            new ShipType("Tanker", new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}, {4, 0}, {5, 0}}),
            new ShipType("Carrier", new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}, {2, 1}, {3, 1}})
    );

    private final List<PlacedShip> playerShips = new ArrayList<>();
    private final List<PlacedShip> aiShips = new ArrayList<>();
    private final Set<String> playerShots = new HashSet<>();
    private final Set<String> aiShots = new HashSet<>();

    private int currentShipIndex = 0;
    private List<int[]> currentShipCoords;
    private boolean isPlacementPhase = true;
    private boolean isPlayerTurn = true;
    private boolean isGameOver = false;
    private int turnCount = 1;

    private Rectangle[][] playerCells = new Rectangle[GRID_SIZE][GRID_SIZE];
    private Rectangle[][] aiCells = new Rectangle[GRID_SIZE][GRID_SIZE];
    
    private GridPane playerGridPane;
    private GridPane aiGridPane;
    private TextFlow logFlow;
    private ScrollPane logScrollPane;
    private Label statusLabel;
    private Button confirmBtn;
    private Button restartBtn;
    
    private VBox leftPanel;

    @Override
    public void start(Stage primaryStage) {
        initPolyglot();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #2c3e50;");

        // Left Panel (Battle Situation)
        leftPanel = new VBox(10);
        leftPanel.setPrefWidth(220);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #34495e; -fx-border-color: #7f8c8d; -fx-border-width: 0 2 0 0;");
        root.setLeft(leftPanel);

        // Grids Container
        HBox gridsBox = new HBox(20);
        gridsBox.setAlignment(Pos.CENTER);
        gridsBox.setPadding(new Insets(10));

        VBox playerBox = createGridBox("YOUR FLEET", playerGridPane = createGrid(true, playerCells));
        VBox aiBox = createGridBox("ENEMY WATERS", aiGridPane = createGrid(false, aiCells));

        gridsBox.getChildren().addAll(playerBox, aiBox);
        root.setCenter(gridsBox);

        // Side Panel (Log)
        VBox sidePanel = new VBox(10);
        sidePanel.setPrefWidth(220);
        sidePanel.setPadding(new Insets(10));
        sidePanel.setStyle("-fx-background-color: #34495e; -fx-border-color: #7f8c8d; -fx-border-width: 0 0 0 2;");

        Label logTitle = new Label("BATTLE LOG");
        logTitle.setTextFill(Color.WHITE);
        logTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        logFlow = new TextFlow();
        logFlow.setPadding(new Insets(5));
        logFlow.setStyle("-fx-background-color: #2c3e50;");
        
        logScrollPane = new ScrollPane(logFlow);
        logScrollPane.setFitToWidth(true);
        logScrollPane.setPrefHeight(500);
        logScrollPane.setStyle("-fx-background: #2c3e50; -fx-border-color: #7f8c8d;");
        logScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        statusLabel = new Label();
        statusLabel.setTextFill(Color.GOLD);
        statusLabel.setStyle("-fx-font-weight: bold;");

        confirmBtn = new Button("Confirm Placement");
        confirmBtn.setMaxWidth(Double.MAX_VALUE);
        confirmBtn.setOnAction(e -> startBattle());

        restartBtn = new Button("Restart Placement");
        restartBtn.setMaxWidth(Double.MAX_VALUE);
        restartBtn.setOnAction(e -> resetGame());

        sidePanel.getChildren().addAll(logTitle, logScrollPane, statusLabel, confirmBtn, restartBtn);
        root.setRight(sidePanel);

        Scene scene = new Scene(root, 1200, 750);
        primaryStage.setTitle("Battleships Pro - GraalVM Edition");
        primaryStage.setScene(scene);
        primaryStage.show();

        resetGame();
    }

    private void initPolyglot() {
        try {
            polyglot = Context.newBuilder("js", "python")
                    .allowAllAccess(true)
                    .build();

            InputStream jsStream = getClass().getResourceAsStream("/ship_logic.js");
            if (jsStream != null) {
                jsEngine = polyglot.eval("js", new Scanner(jsStream).useDelimiter("\\A").next());
            } else {
                System.err.println("Could not find ship_logic.js resource");
            }

            InputStream pyStream = getClass().getResourceAsStream("/ai_logic.py");
            if (pyStream != null) {
                pythonAI = polyglot.eval("python", new Scanner(pyStream).useDelimiter("\\A").next());
            } else {
                System.err.println("Could not find ai_logic.py resource");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize Polyglot engines: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetGame() {
        playerShips.clear();
        aiShips.clear();
        playerShots.clear();
        aiShots.clear();
        currentShipIndex = 0;
        isPlacementPhase = true;
        isPlayerTurn = true;
        isGameOver = false;
        turnCount = 1;
        confirmBtn.setVisible(false);
        restartBtn.setVisible(false);
        logFlow.getChildren().clear();
        log("--- PLACEMENT PHASE ---");
        log("Click to place ships. Right-click to rotate.");
        prepareNextShip();
        updateGrids();
        updateBattleSituation();
    }

    private void prepareNextShip() {
        if (currentShipIndex < shipTypes.size()) {
            currentShipCoords = shipTypes.get(currentShipIndex).getCoords();
        } else {
            isPlacementPhase = false;
            confirmBtn.setVisible(true);
            restartBtn.setVisible(true);
        }
        updateStatus();
    }

    private void updateStatus() {
        if (isPlacementPhase) {
            if (currentShipIndex < shipTypes.size()) {
                statusLabel.setText("Placing: " + shipTypes.get(currentShipIndex).name);
            } else {
                statusLabel.setText("Confirm your placement!");
            }
        } else if (isGameOver) {
            // Handled in checkWin
        } else {
            statusLabel.setText(String.format("Turn %d: %s", turnCount, isPlayerTurn ? "YOUR TURN" : "AI THINKING..."));
        }
    }

    private VBox createGridBox(String title, GridPane grid) {
        Label label = new Label(title);
        label.setTextFill(Color.WHITE);
        label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        VBox box = new VBox(5, label, grid);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private GridPane createGrid(boolean isPlayer, Rectangle[][] cells) {
        GridPane grid = new GridPane();
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                Rectangle rect = new Rectangle(CELL_SIZE, CELL_SIZE);
                rect.setFill(Color.web("#34495e"));
                rect.setStroke(Color.web("#7f8c8d", 0.3));
                rect.setPickOnBounds(true);
                
                Tooltip.install(rect, new Tooltip(formatCoord(x, y)));
                
                final int fx = x;
                final int fy = y;

                rect.setOnMouseEntered(e -> {
                    if (isPlacementPhase && isPlayer) highlightPlacement(fx, fy);
                });

                rect.setOnMouseClicked(e -> {
                    if (isPlayer) {
                        if (isPlacementPhase) {
                            if (e.getButton() == MouseButton.PRIMARY) {
                                placePlayerShip(fx, fy);
                            } else if (e.getButton() == MouseButton.SECONDARY) {
                                rotateCurrentShip();
                                highlightPlacement(fx, fy);
                            }
                        }
                    } else {
                        if (!isPlacementPhase && isPlayerTurn && !isGameOver) {
                            handlePlayerShot(fx, fy);
                        }
                    }
                });

                cells[x][y] = rect;
                grid.add(rect, x, y);
            }
        }
        return grid;
    }

    private void highlightPlacement(int x, int y) {
        // Fast update using the 2D array
        updateGrid(playerCells, playerShips, aiShots, true);
        
        if (currentShipCoords == null || currentShipIndex >= shipTypes.size()) return;

        List<int[]> absCoords = currentShipCoords.stream()
                .map(p -> new int[]{x + p[0], y + p[1]})
                .collect(Collectors.toList());

        boolean legal = isPlacementLegal(absCoords, playerShips);
        Color color = legal ? Color.web("#2ecc71", 0.7) : Color.web("#e74c3c", 0.7);

        for (int[] p : absCoords) {
            if (p[0] >= 0 && p[0] < GRID_SIZE && p[1] >= 0 && p[1] < GRID_SIZE) {
                playerCells[p[0]][p[1]].setFill(color);
            }
        }
    }

    private void rotateCurrentShip() {
        if (jsEngine == null) {
            log("Error: JS Engine not initialized!");
            return;
        }
        try {
            Value rotated = jsEngine.getMember("rotateShip").execute(toJSCoords(currentShipCoords));
            currentShipCoords = fromJSCoords(rotated);
        } catch (Exception e) {
            System.err.println("Rotation failed: " + e.getMessage());
        }
    }

    private void placePlayerShip(int x, int y) {
        if (currentShipIndex >= shipTypes.size() || jsEngine == null) return;
        
        try {
            List<List<Map<String, Integer>>> existingJS = playerShips.stream()
                    .map(s -> toJSCoords(s.coords))
                    .collect(Collectors.toList());
            
            Value result = jsEngine.getMember("placeShip").execute(x, y, toJSCoords(currentShipCoords), existingJS);
            
            if (result != null && !result.isNull()) {
                List<int[]> absCoords = fromJSCoords(result);
                playerShips.add(new PlacedShip(shipTypes.get(currentShipIndex).name, absCoords, shipTypes.get(currentShipIndex).getCoords()));
                log("Placed " + shipTypes.get(currentShipIndex).name);
                currentShipIndex++;
                prepareNextShip();
                updateGrids();
                updateBattleSituation();
            } else {
                log("Invalid placement!");
            }
        } catch (Exception e) {
            System.err.println("Placement failed: " + e.getMessage());
        }
    }

    private boolean isPlacementLegal(List<int[]> coords, List<PlacedShip> existing) {
        if (jsEngine == null) return false;
        try {
            List<List<Map<String, Integer>>> existingJS = existing.stream()
                    .map(s -> toJSCoords(s.coords))
                    .collect(Collectors.toList());
            
            return jsEngine.getMember("isPlacementLegal").execute(toJSCoords(coords), existingJS).asBoolean();
        } catch (Exception e) {
            System.err.println("Placement validation failed: " + e.getMessage());
            return false;
        }
    }

    private void startBattle() {
        confirmBtn.setVisible(false);
        restartBtn.setVisible(false);
        
        // AI places ships
        List<List<int[]>> shipDefs = shipTypes.stream().map(ShipType::getCoords).collect(Collectors.toList());
        Value aiPlaced = pythonAI.getMember("ai").getMember("place_ships").execute(shipDefs);
        
        for (int i = 0; i < shipTypes.size(); i++) {
            Value ship = aiPlaced.getArrayElement(i);
            List<int[]> coords = new ArrayList<>();
            for (int j = 0; j < ship.getArraySize(); j++) {
                Value p = ship.getArrayElement(j);
                coords.add(new int[]{p.getArrayElement(0).asInt(), p.getArrayElement(1).asInt()});
            }
            aiShips.add(new PlacedShip(shipTypes.get(i).name, coords, shipTypes.get(i).getCoords()));
        }
        
        log("--- BATTLE START ---");
        log("Click a square on enemy grid to shoot");
        log(String.format("Turn %d:", turnCount));
        updateStatus();
        updateGrids();
        updateBattleSituation();
    }

    private void handlePlayerShot(int x, int y) {
        String coord = x + "," + y;
        if (playerShots.contains(coord)) return;

        playerShots.add(coord);
        boolean hit = false;
        PlacedShip hitShip = null;
        for (PlacedShip s : aiShips) {
            if (s.isAt(x, y)) {
                hit = true;
                s.hits++;
                if (s.isSunk()) hitShip = s;
                break;
            }
        }

        Color logColor = hit ? Color.web("#3498db") : Color.web("#ecf0f1");
        log(String.format("You shoot at %s, %s", formatCoord(x, y), hit ? "HIT!" : "MISS"), logColor);
        
        if (hitShip != null) {
            log("You SUNK an enemy " + hitShip.name + "!", Color.web("#2ecc71"));
        }

        updateGrids();
        updateBattleSituation();
        if (checkWin()) return;

        isPlayerTurn = false;
        updateStatus();
        new Thread(() -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            Platform.runLater(this::handleAIShot);
        }).start();
    }

    private void handleAIShot() {
        Value shot = pythonAI.getMember("ai").getMember("make_shot").execute();
        int x = shot.getArrayElement(0).asInt();
        int y = shot.getArrayElement(1).asInt();

        String coord = x + "," + y;
        aiShots.add(coord);
        
        boolean hit = false;
        PlacedShip hitShip = null;
        for (PlacedShip s : playerShips) {
            if (s.isAt(x, y)) {
                hit = true;
                s.hits++;
                if (s.isSunk()) hitShip = s;
                break;
            }
        }

        Color logColor = hit ? Color.web("#f1c40f") : Color.web("#ecf0f1");
        log(String.format("AI shoots at %s, %s", formatCoord(x, y), hit ? "HIT!" : "MISS"), logColor);
        
        if (hitShip != null) {
            log("AI SUNK your " + hitShip.name + "!", Color.web("#e74c3c"));
        }

        // Update AI logic
        pythonAI.getMember("ai").getMember("update_result").execute(x, y, hit, hitShip != null, 
                hitShip != null ? toPyCoords(hitShip.coords) : null);

        updateGrids();
        updateBattleSituation();
        if (checkWin()) return;

        isPlayerTurn = true;
        turnCount++;
        log(String.format("Turn %d:", turnCount));
        updateStatus();
    }

    private boolean checkWin() {
        if (isGameOver) return true;
        
        boolean playerWin = aiShips.stream().allMatch(PlacedShip::isSunk);
        boolean aiWin = playerShips.stream().allMatch(PlacedShip::isSunk);

        if (playerWin) {
            statusLabel.setText("VICTORY! You won!");
            log("--- GAME OVER: YOU WIN ---", Color.web("#2ecc71"));
            isGameOver = true;
            return true;
        } else if (aiWin) {
            statusLabel.setText("DEFEAT! AI won!");
            log("--- GAME OVER: AI WINS ---", Color.web("#e74c3c"));
            isGameOver = true;
            return true;
        }
        return false;
    }

    private void updateGrids() {
        updateGrid(playerCells, playerShips, aiShots, true);
        updateGrid(aiCells, aiShips, playerShots, false);
    }

    private void updateGrid(Rectangle[][] cells, List<PlacedShip> ships, Set<String> shots, boolean showShips) {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                Rectangle r = cells[x][y];
                r.setFill(Color.web("#34495e")); // Default cell color
                
                final int fx = x;
                final int fy = y;
                
                boolean isShip = ships.stream().anyMatch(s -> s.isAt(fx, fy));
                boolean isShot = shots.contains(fx + "," + fy);
                
                if (isShot) {
                    if (isShip) {
                        PlacedShip s = ships.stream().filter(ship -> ship.isAt(fx, fy)).findFirst().orElse(null);
                        r.setFill(s != null && s.isSunk() ? Color.DARKRED : Color.RED);
                    } else {
                        r.setFill(Color.LIGHTBLUE);
                    }
                } else if (showShips && isShip) {
                    r.setFill(Color.GRAY);
                }
            }
        }
    }

    private void updateBattleSituation() {
        leftPanel.getChildren().clear();
        
        Label title = new Label("BATTLE SITUATION");
        title.setTextFill(Color.WHITE);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        leftPanel.getChildren().add(title);

        leftPanel.getChildren().add(createShipStatusBox("YOUR SHIPS", playerShips));
        
        Region spacer = new Region();
        spacer.setPrefHeight(20);
        leftPanel.getChildren().add(spacer);
        
        leftPanel.getChildren().add(createShipStatusBox("ENEMY SHIPS", aiShips));
    }

    private VBox createShipStatusBox(String title, List<PlacedShip> ships) {
        VBox box = new VBox(5);
        Label lbl = new Label(title);
        lbl.setTextFill(Color.GOLD);
        lbl.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(lbl);
        
        if (ships.isEmpty()) {
            Label empty = new Label("(Not placed)");
            empty.setTextFill(Color.GRAY);
            empty.setStyle("-fx-font-style: italic;");
            box.getChildren().add(empty);
        } else {
            for (PlacedShip s : ships) {
                HBox row = new HBox(5);
                row.setAlignment(Pos.CENTER_LEFT);
                
                Label nameLbl = new Label(String.format("%-15s", s.name));
                nameLbl.setTextFill(Color.WHITE);
                nameLbl.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");
                
                HBox shape = createShipShapeNode(s);
                
                Label statusLbl = new Label(s.isSunk() ? "SUNK" : "ALIVE");
                statusLbl.setTextFill(s.isSunk() ? Color.web("#e74c3c") : Color.web("#2ecc71"));
                statusLbl.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 10px;");
                
                row.getChildren().addAll(nameLbl, shape, statusLbl);
                box.getChildren().add(row);
            }
        }
        return box;
    }

    private HBox createShipShapeNode(PlacedShip ship) {
        GridPane miniGrid = new GridPane();
        miniGrid.setHgap(1);
        miniGrid.setVgap(1);
        
        for (int[] p : ship.originalCoords) {
            Rectangle r = new Rectangle(3, 3);
            r.setFill(ship.isSunk() ? Color.web("#e74c3c") : Color.web("#2ecc71"));
            miniGrid.add(r, p[0], p[1]);
        }
        
        HBox container = new HBox(miniGrid);
        container.setAlignment(Pos.CENTER);
        container.setPrefWidth(35);
        return container;
    }

    private String formatCoord(int x, int y) {
        return String.format("%c %02d", 'A' + x, y + 1);
    }

    private void log(String msg) {
        log(msg, Color.web("#ecf0f1"));
    }

    private void log(String msg, Color color) {
        Platform.runLater(() -> {
            Text text = new Text(msg + "\n");
            text.setFill(color);
            text.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px;");
            logFlow.getChildren().add(text);
            // Auto-scroll
            logScrollPane.setVvalue(1.0);
        });
    }

    // Conversion Helpers
    private List<Map<String, Integer>> toJSCoords(List<int[]> coords) {
        return coords.stream().map(p -> {
            Map<String, Integer> m = new HashMap<>();
            m.put("x", p[0]);
            m.put("y", p[1]);
            return m;
        }).collect(Collectors.toList());
    }

    private List<int[]> fromJSCoords(Value val) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < val.getArraySize(); i++) {
            Value p = val.getArrayElement(i);
            list.add(new int[]{p.getMember("x").asInt(), p.getMember("y").asInt()});
        }
        return list;
    }

    private List<List<Integer>> toPyCoords(List<int[]> coords) {
        return coords.stream().map(p -> Arrays.asList(p[0], p[1])).collect(Collectors.toList());
    }

    private static class ShipType {
        String name;
        int[][] coords;
        ShipType(String name, int[][] coords) { this.name = name; this.coords = coords; }
        List<int[]> getCoords() { return Arrays.stream(coords).map(int[]::clone).collect(Collectors.toList()); }
    }

    private static class PlacedShip {
        String name;
        List<int[]> coords;
        List<int[]> originalCoords;
        int hits = 0;
        PlacedShip(String name, List<int[]> coords, List<int[]> originalCoords) { 
            this.name = name; 
            this.coords = coords; 
            this.originalCoords = originalCoords;
        }
        boolean isAt(int x, int y) { return coords.stream().anyMatch(p -> p[0] == x && p[1] == y); }
        boolean isSunk() { return hits >= coords.size(); }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
