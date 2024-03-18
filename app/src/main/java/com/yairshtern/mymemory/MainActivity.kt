package com.yairshtern.mymemory

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.squareup.picasso.Picasso
import com.yairshtern.mymemory.models.BoardSize
import com.yairshtern.mymemory.models.MemoryGame
import com.yairshtern.mymemory.models.UserImageList
import com.yairshtern.mymemory.utils.EXTRA_BOARD_SIZE
import com.yairshtern.mymemory.utils.EXTRA_GAME_NAME
import java.util.Locale


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }

    private lateinit var clRoot: CoordinatorLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView

    private val db = Firebase.firestore
    private var gameName: String? = null
    private var customGameImages: List<String>? = null
    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter: MemoryBoardAdapter
    private var boardSize: BoardSize = BoardSize.EASY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setupBoard()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCards()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame()) {
                    showAlertDialog(
                        getString(R.string.quit_your_current_game),
                        null,
                        View.OnClickListener {
                            setupBoard()
                        })
                } else {
                    setupBoard()
                }
                return true
            }

            R.id.mi_new_size -> {
                showNewSizeDialog()
                return true
            }

            R.id.mi_costum -> {
                showCreationDialog()
                return true
            }

            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }

            R.id.mi_language -> {
                chooseLanguage()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if (customGameName == null) {
                Log.e(TAG, "Got null custom game name from CreateActivity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun chooseLanguage() {
        val languageView = LayoutInflater.from(this).inflate(R.layout.dialog_choose_language, null)
        val radioGroupLanguage = languageView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog(
            getString(R.string.choose_language),
            languageView,
            View.OnClickListener {
                // Set language
                val config = resources.configuration
                when (radioGroupLanguage.checkedRadioButtonId) {
                    R.id.rbHebrew -> config.setLocale(Locale("iw"))
                    R.id.rbEnglish -> config.setLocale(Locale.US)
                }
                resources.updateConfiguration(config, resources.displayMetrics)
                updateStrings()
            })
    }

    @SuppressLint("StringFormatMatches", "RestrictedApi")
    private fun updateStrings() {
        this.invalidateOptionsMenu();
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        tvNumMoves.text = getString(R.string.moves, memoryGame.getNumMoves())
        tvNumPairs.text =
            getString(R.string.pairs, memoryGame.numPairsFound, boardSize.getNumPairs())
    }

    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        showAlertDialog(
            getString(R.string.create_your_own_memory_board),
            boardSizeView,
            View.OnClickListener {
                // Set a new value for the board size
                val desireBoardSize = when (radioGroupSize.checkedRadioButtonId) {
                    R.id.rbMedium -> BoardSize.MEDIUM
                    R.id.rbEasy -> BoardSize.EASY
                    else -> BoardSize.HARD
                }
                // navigate to a new activity
                val intent = Intent(this, CreateActivity::class.java)
                intent.putExtra(EXTRA_BOARD_SIZE, desireBoardSize)
                startActivityForResult(intent, CREATE_REQUEST_CODE)
            })
    }

    private fun updateCards() {
        adapter = MemoryBoardAdapter(
            this,
            boardSize,
            memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClicked(position: Int) {
                    updateGameWithFlip(position)
                }

            })
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        val orientation = resources.configuration.orientation
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth(orientation))
    }

    private fun showDownloadDialog() {
        val boardDownloadView =
            LayoutInflater.from(this).inflate(R.layout.dialog_download_board, null)
        showAlertDialog(
            getString(R.string.fetch_memory_game),
            boardDownloadView,
            View.OnClickListener {
                // Grab the text of the game name that the user wants to download
                val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
                val gameToDownload = etDownloadGame.text.toString().trim()
                downloadGame(gameToDownload)
            })
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document ->
            val userImageList = document.toObject(UserImageList::class.java)
            if (userImageList?.images == null) {
                Log.e(TAG, "Invalid custom game data from Firestore")
                Snackbar.make(
                    clRoot,
                    getString(R.string.sorry_we_couldn_t_find_any_such_game, customGameName),
                    Snackbar.LENGTH_LONG
                ).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)
            customGameImages = userImageList.images
            for (imageUrl in userImageList.images) {
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(
                clRoot,
                getString(R.string.you_re_now_playing, customGameName), Snackbar.LENGTH_LONG
            )
                .show()
            gameName = customGameName
            setupBoard()
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Exception when retrieving game", exception)
        }
    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this).inflate(R.layout.dialog_board_size, null)
        val radioGroupSize = boardSizeView.findViewById<RadioGroup>(R.id.radioGroup)
        when (boardSize) {
            BoardSize.EASY -> radioGroupSize.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroupSize.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroupSize.check(R.id.rbHard)
        }

        showAlertDialog(getString(R.string.choose_new_size), boardSizeView, View.OnClickListener {
            // Set a new value for the board size
            boardSize = when (radioGroupSize.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            gameName = null
            customGameImages = null
            setupBoard()
        })
    }

    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveButtonClickListener: View.OnClickListener
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                positiveButtonClickListener.onClick(null)
            }.show()
    }

    private fun setupBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = getString(R.string.easy_4_x_2)
                tvNumPairs.text = getString(R.string.pairs_0_4)
            }

            BoardSize.MEDIUM -> {
                tvNumMoves.text = getString(R.string.medium_6_x_3)
                tvNumPairs.text = getString(R.string.pairs_0_9)
            }

            BoardSize.HARD -> {
                tvNumMoves.text = getString(R.string.hard_8_x_4)
                tvNumPairs.text = getString(R.string.pairs_0_16)
            }
        }
        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)
        updateCards()
    }


    @SuppressLint("SetTextI18n", "NotifyDataSetChanged", "StringFormatMatches")
    private fun updateGameWithFlip(position: Int) {
        // Error checking
        if (memoryGame.haveWonGame()) {
            // Alert the user of an invalid move
            Snackbar.make(clRoot, getString(R.string.you_already_won), Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.isCardFaceUp(position)) {
            // Alert the user of an invalid move
            Snackbar.make(clRoot, getString(R.string.invalid_move), Snackbar.LENGTH_SHORT).show()
            return
        }
        // Actually flip over the card
        if (memoryGame.flipCard(position)) {
            Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text =
                getString(R.string.pairs, memoryGame.numPairsFound, boardSize.getNumPairs())
            if (memoryGame.haveWonGame()) {
                Snackbar.make(
                    clRoot,
                    getString(R.string.you_won_congratulations),
                    Snackbar.LENGTH_SHORT
                ).show()
                CommonConfetti.rainingConfetti(
                    clRoot,
                    intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA, Color.BLUE)
                ).oneShot()
            }
        }
        tvNumMoves.text = getString(R.string.moves, memoryGame.getNumMoves())
        adapter.notifyDataSetChanged()
    }
}