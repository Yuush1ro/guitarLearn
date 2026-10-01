package com.example.guitartuner;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.guitartuner.fragments.LessonsFragment;
import com.example.guitartuner.fragments.MetronomeFragment;
import com.example.guitartuner.fragments.NoteTrainerFragment;
import com.example.guitartuner.fragments.SongsFragment;
import com.example.guitartuner.fragments.TunerFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // порядок вкладок слева направо — от него зависит, с какой стороны въезжает раздел
    private static final int[] TAB_ORDER = {
            R.id.nav_tuner, R.id.nav_lessons, R.id.nav_trainer, R.id.nav_songs, R.id.nav_metronome
    };

    private BottomNavigationView bottomNavigationView;
    private int currentTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottomNavigation);

        // На Android 15+ приложение рисуется под статус-баром (edge-to-edge) —
        // сдвигаем экраны вниз на его высоту. Нижнюю панель BottomNavigationView учитывает сама.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragmentContainer), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new TunerFragment())
                    .commit();
        } else {
            currentTab = tabIndex(bottomNavigationView.getSelectedItemId());
        }

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = createTab(item.getItemId());
            if (fragment == null) return false;
            switchTab(fragment, tabIndex(item.getItemId()));
            return true;
        });
        // повторное нажатие на текущую вкладку не пересоздаёт экран
        bottomNavigationView.setOnItemReselectedListener(item -> {
        });
    }

    private static Fragment createTab(int itemId) {
        if (itemId == R.id.nav_tuner) return new TunerFragment();
        if (itemId == R.id.nav_lessons) return new LessonsFragment();
        if (itemId == R.id.nav_trainer) return new NoteTrainerFragment();
        if (itemId == R.id.nav_songs) return new SongsFragment();
        if (itemId == R.id.nav_metronome) return new MetronomeFragment();
        return null;
    }

    private static int tabIndex(int itemId) {
        for (int i = 0; i < TAB_ORDER.length; i++) {
            if (TAB_ORDER[i] == itemId) return i;
        }
        return 0;
    }

    /** Новая вкладка въезжает с той стороны, где она находится в нижней панели. */
    private void switchTab(Fragment fragment, int newTab) {
        FragmentManager fm = getSupportFragmentManager();
        // открытый урок/песня относятся к прежней вкладке — "назад" не должен вести в чужой раздел
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        boolean toRight = newTab > currentTab;
        currentTab = newTab;
        fm.beginTransaction()
                .setReorderingAllowed(true)
                .setCustomAnimations(
                        toRight ? R.anim.tab_enter_from_right : R.anim.tab_enter_from_left,
                        toRight ? R.anim.tab_exit_to_left : R.anim.tab_exit_to_right)
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
