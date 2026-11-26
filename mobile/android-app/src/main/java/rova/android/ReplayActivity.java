package rova.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import rova.Inspection;
import rova.Observation;
import rova.Replay;
import rova.ReplayResult;
import rova.State;

/** Native, deterministic fixture viewer. No camera or robot is used by this screen. */
public class ReplayActivity extends Activity {
    private static final String SELECTED = "selected_scenario";
    private static final String[] IDS = {"correct", "mismatch", "closed", "takeover", "blur-off"};
    private static final String[] LABELS = {
        "Correct room", "Plate mismatch", "Closed door", "Manual takeover", "Face blur off"
    };
    private String selected = "correct";
    private TextView summary;
    private TextView guidance;
    private TextView observations;
    private TextView history;
    private final Button[] scenarioButtons = new Button[IDS.length];

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Rova simulation replay");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);

        heading(content, "SIMULATION • Replay", 26);
        paragraph(content, "Run a simulated patrol with recorded test data.");
        Button camera = new Button(this);
        camera.setText("Capture a doorway photo");
        camera.setMinHeight(dp(48));
        camera.setOnClickListener(view -> {
            Intent intent = new Intent();
            intent.setClassName(this, "rova.android.CameraActivity");
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                guidance.setText("The camera screen is unavailable in this build.");
                guidance.announceForAccessibility(guidance.getText());
            }
        });
        content.addView(camera);
        heading(content, "Choose a scenario", 20);
        for (int i = 0; i < IDS.length; i++) {
            final String id = IDS[i];
            Button button = new Button(this);
            scenarioButtons[i] = button;
            button.setText(LABELS[i]);
            button.setMinHeight(dp(48));
            button.setOnClickListener(view -> runScenario(id));
            content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }

        heading(content, "Replay result", 20);
        summary = paragraph(content, "");
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        guidance = paragraph(content, "");
        heading(content, "Recorded observations", 20);
        observations = paragraph(content, "");
        observations.setTextIsSelectable(true);
        heading(content, "State history", 20);
        history = paragraph(content, "");
        history.setTextIsSelectable(true);

        if (savedInstanceState != null) {
            String restored = savedInstanceState.getString(SELECTED, "correct");
            for (String id : IDS) if (id.equals(restored)) selected = restored;
        }
        runScenario(selected);
    }

    private void runScenario(String id) {
        selected = id;
        for (int i = 0; i < IDS.length; i++) {
            boolean active = IDS[i].equals(id);
            scenarioButtons[i].setText(LABELS[i] + (active ? " — selected" : ""));
            scenarioButtons[i].setContentDescription(LABELS[i] + (active ? ", selected. Run simulation" : ". Run simulation"));
        }
        try {
            ReplayResult result = Replay.run(id);
            summary.setText("SIMULATION • " + scenarioLabel(id) + "\nFinal state: "
                    + readable(result.getState()) + "\nRecorded observations: " + result.getObservations().size()
                    + "\nFixture face blur: " + ("blur-off".equals(id) ? "OFF (synthetic data only)" : "ON"));
            if (result.getState() == State.UncertainLocation) {
                guidance.setText("The plate did not match Lab 3. No observation was recorded. "
                        + "An operator would recheck the plate, confirm the room, or skip the stop. "
                        + "Choose Correct room to compare the successful replay.");
            } else if (result.getState() == State.ManualTakeover) {
                guidance.setText("Manual takeover ended autonomous processing. The stale inspection was ignored "
                        + "and no observation was recorded. Choose another scenario to run a fresh simulation.");
            } else if ("closed".equals(id)) {
                guidance.setText("The closed door was recorded. Occupancy and equipment visibility cannot be determined; "
                        + "this does not establish that the room is empty.");
            } else {
                guidance.setText("Replay completed and returned home. No one visible in the fixture does not "
                        + "establish that the whole room is empty. Choose another scenario to compare behavior.");
            }
            StringBuilder evidence = new StringBuilder();
            int number = 0;
            for (Observation observation : result.getObservations()) {
                Inspection inspection = observation.getInspection();
                if (number > 0) evidence.append("\n\n");
                evidence.append("Observation ").append(++number)
                        .append("\nVerified room: ").append(observation.getVerifiedRoomId())
                        .append("\nDoor: ").append(readable(inspection.getDoor()))
                        .append("\nOccupancy: ").append(readable(inspection.getOccupancy()))
                        .append("\nVisible count: ").append(inspection.getCount() == null ? "Cannot determine" : inspection.getCount())
                        .append("\nWhole room visible: ").append(inspection.getWholeRoomVisible() ? "Yes" : "No")
                        .append("\nLights: ").append(readable(inspection.getLights()))
                        .append("\nProjector: ").append(readable(inspection.getProjector()))
                        .append("\nPrivacy applied: ").append(observation.getFrame().getPrivacyApplied())
                        .append("\nSynthetic evidence: ").append(observation.getFrame().getEvidenceRef())
                        .append("\nExecution mode: ").append(observation.getExecutionMode())
                        .append("\nPerception provider: ").append(observation.getPerceptionProvider());
            }
            observations.setText(number == 0 ? "No observations recorded for this scenario." : evidence.toString());
            StringBuilder steps = new StringBuilder();
            int step = 0;
            for (State state : result.getHistory()) {
                if (step > 0) steps.append('\n');
                steps.append(++step).append(". ").append(readable(state));
            }
            history.setText(steps.length() == 0 ? "No state history returned." : steps.toString());
        } catch (RuntimeException exception) {
            summary.setText("Simulation failed • " + scenarioLabel(id));
            guidance.setText("This fixture could not complete. Run it again or choose another scenario.");
            observations.setText("No replay result is available.");
            history.setText("No state history is available for this attempt.");
            android.util.Log.e("RovaReplay", "Replay failed: " + id, exception);
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putString(SELECTED, selected);
        super.onSaveInstanceState(outState);
    }

    private String scenarioLabel(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return id;
    }

    private static String readable(Object value) {
        return String.valueOf(value).replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private TextView paragraph(LinearLayout parent, String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(17);
        view.setTextColor(0xFF17212B);
        view.setPadding(0, dp(6), 0, dp(12));
        parent.setBackgroundColor(0xFFFFFFFF);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void heading(LinearLayout parent, String text, int size) {
        TextView view = paragraph(parent, text);
        view.setTextSize(size);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        if (android.os.Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
