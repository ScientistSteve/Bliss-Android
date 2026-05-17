package net.kdt.pojavlaunch.ai;

import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiChatViewModel extends ViewModel {
    public final List<AiChatAdapter.Message> messages = new ArrayList<>();
    public final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    public int editingUserIndex = RecyclerView.NO_POSITION;
    public int typingIndex = RecyclerView.NO_POSITION;
    public int requestGeneration = 0;

    @Override
    protected void onCleared() {
        networkExecutor.shutdownNow();
        super.onCleared();
    }
}
