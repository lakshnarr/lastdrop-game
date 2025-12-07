package earth.lastdrop.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedGamesActivity : AppCompatActivity() {

    private lateinit var savedGameDao: SavedGameDao
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SavedGamesAdapter
    private lateinit var btnDeleteAll: Button
    private lateinit var btnClose: Button
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_games)

        savedGameDao = LastDropDatabase.getInstance(this).savedGameDao()
        recyclerView = findViewById(R.id.savedGamesList)
        btnDeleteAll = findViewById(R.id.btnDeleteAllSaves)
        btnClose = findViewById(R.id.btnCloseSavedGames)

        adapter = SavedGamesAdapter(
            onLoad = { saved ->
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("savedGameId", saved.savedGameId)
                startActivity(intent)
                finish()
            },
            onDelete = { saved ->
                confirmDelete(saved)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnDeleteAll.setOnClickListener { confirmDeleteAll() }
        btnClose.setOnClickListener { finish() }

        loadSavedGames()
    }

    private fun loadSavedGames() {
        scope.launch(Dispatchers.IO) {
            val saves = runCatching { savedGameDao.getAll() }.getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                adapter.submitList(saves)
                if (saves.isEmpty()) {
                    Toast.makeText(this@SavedGamesActivity, "No saved games found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmDelete(saved: SavedGame) {
        AlertDialog.Builder(this)
            .setTitle("Delete save")
            .setMessage("Delete this saved game? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteSavedGame(saved) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Delete all saves")
            .setMessage("Delete all saved games? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ -> deleteAllSavedGames() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSavedGame(saved: SavedGame) {
        scope.launch(Dispatchers.IO) {
            runCatching { savedGameDao.deleteById(saved.savedGameId) }
            loadSavedGames()
        }
    }

    private fun deleteAllSavedGames() {
        scope.launch(Dispatchers.IO) {
            runCatching { savedGameDao.clearAll() }
            loadSavedGames()
        }
    }

    private class SavedGamesAdapter(
        private val onLoad: (SavedGame) -> Unit,
        private val onDelete: (SavedGame) -> Unit
    ) : RecyclerView.Adapter<SavedGamesAdapter.SavedGameViewHolder>() {

        private var saves: List<SavedGame> = emptyList()
        private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun submitList(newList: List<SavedGame>) {
            saves = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedGameViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_game, parent, false)
            return SavedGameViewHolder(view)
        }

        override fun getItemCount(): Int = saves.size

        override fun onBindViewHolder(holder: SavedGameViewHolder, position: Int) {
            val item = saves[position]
            holder.bind(item, formatter, onLoad, onDelete)
        }

        class SavedGameViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.savedGameTitle)
            private val subtitle: TextView = itemView.findViewById(R.id.savedGameSubtitle)
            private val btnLoad: Button = itemView.findViewById(R.id.btnLoadSaved)
            private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteSaved)

            fun bind(
                item: SavedGame,
                formatter: SimpleDateFormat,
                onLoad: (SavedGame) -> Unit,
                onDelete: (SavedGame) -> Unit
            ) {
                title.text = item.label.ifBlank { "Saved game" }
                val timeText = formatter.format(Date(item.savedAt))
                val playersText = if (item.playerCount > 0) "${item.playerCount} players" else "Players unknown"
                val diceText = if (item.playWithTwoDice) "• two dice" else ""
                subtitle.text = "$timeText • $playersText $diceText".trim()

                btnLoad.setOnClickListener { onLoad(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}
