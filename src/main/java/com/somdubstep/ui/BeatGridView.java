
package com.somdubstep.ui;

import com.somdubstep.pattern.BeatPattern;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import java.util.HashMap;
import java.util.Map;

public class BeatGridView extends VBox {
    private final GridPane kickGrid = new GridPane();
    private final GridPane snareGrid = new GridPane();
    private final GridPane hihatGrid = new GridPane();
    private final GridPane bassGrid = new GridPane();

    private final Button kickMute = new Button("M");
    private final Button snareMute = new Button("M");
    private final Button hihatMute = new Button("M");
    private final Button bassMute = new Button("M");

    private BeatPattern pattern;
    private int highlighted = -1;
    private  Map<String, Boolean> mutes = new HashMap<>();

    public BeatGridView(){
        setSpacing(10);
        setPadding(new Insets(10));

        mutes.put("kick", false);
        mutes.put("snare", false);
        mutes.put("hihat", false);
        mutes.put("bass", false);

        getChildren().add(row("Kick:", kickGrid, kickMute, "kick"));
        getChildren().add(row("Snare:", snareGrid, snareMute, "snare"));
        getChildren().add(row("Hihat:", hihatGrid, hihatMute, "hihat"));
        getChildren().add(row("Bass:", bassGrid, bassMute, "bass"));
    }

    private HBox row(String label, GridPane grid, Button muteBtn, String key){
        Label l = new Label(label);
        l.setMinWidth(60);
        grid.setHgap(2);
        HBox hb = new HBox(10, muteBtn, l, grid);
        hb.setAlignment(Pos.CENTER_LEFT);
        muteBtn.setOnAction(e -> toggleMute(key, muteBtn, l, grid));
        return hb;
    }

    public void setPattern(BeatPattern p){
        this.pattern = p;
        buildGrid(kickGrid, p.kick, "kick");
        buildGrid(snareGrid, p.snare, "snare");
        buildGrid(hihatGrid, p.hihat, "hihat");
        buildGrid(bassGrid, p.bass, "bass");
    }

    private void buildGrid(GridPane grid, boolean[] data, String key){
        grid.getChildren().clear();
        int steps = data.length;
        for(int i=0;i<steps;i++){
            StackPane cell = new StackPane();
            cell.setPrefSize(20,20);
            cell.setStyle(cellStyle(data[i], false));
            final int idx=i;
            cell.setOnMouseClicked(ev -> {
                if(ev.getButton()== MouseButton.PRIMARY){
                    data[idx] = !data[idx];
                    cell.setStyle(cellStyle(data[idx], idx==highlighted));
                }
            });
            cell.setCursor(Cursor.HAND);
            grid.add(cell, i, 0);
        }
    }

    public void highlight(int step){
        this.highlighted = step;
        if(pattern==null) return;
        updateHighlightRow(kickGrid, pattern.kick);
        updateHighlightRow(snareGrid, pattern.snare);
        updateHighlightRow(hihatGrid, pattern.hihat);
        updateHighlightRow(bassGrid, pattern.bass);
    }

    private void updateHighlightRow(GridPane grid, boolean[] data){
        int steps = data.length;
        for(Node n : grid.getChildren()){
            Integer col = GridPane.getColumnIndex(n);
            int i = col==null?0:col;
            boolean isHL = (i==highlighted);
            ((Region)n).setStyle(cellStyle(data[i], isHL));
        }
    }

    private void toggleMute(String key, Button btn, Label label, GridPane grid){
        boolean newState = !mutes.get(key);
        mutes.put(key, newState);
        btn.setText(newState ? "X" : "M");
        label.setStyle(newState ? "-fx-text-fill: #777; -fx-strikethrough: true;" : "");
        grid.setOpacity(newState ? 0.3 : 1.0);
    }

    public boolean isMuted(String key){
        return mutes.getOrDefault(key, false);
    }

    private static String cellStyle(boolean active, boolean highlight){
        String bg = active ? "#00ff88" : "#111";
        if(highlight) bg = "#ff0066";
        return "-fx-background-color: "+bg+"; -fx-border-color: #333; -fx-border-radius: 2; -fx-background-radius: 2;";
    }
}
