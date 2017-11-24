package com.andretietz.auth.firebase

import com.andretietz.auth.AuthClient
import com.andretietz.auth.AuthCredential
import com.andretietz.auth.credentials.EmailCredential
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import io.reactivex.Single
import io.reactivex.SingleEmitter
import java.util.concurrent.Executors

class FirebaseAuthClient<T>(private val userFactory: UserFactory<T>) : AuthClient<T> {


    private val firebaseAuth = FirebaseAuth.getInstance()
    private val mapper = FirebaseCredentialMapper()
    private val backgroundExecutor = Executors.newSingleThreadExecutor()


    override fun signUp(credential: AuthCredential): Single<T> {
        return Single.create({ emitter ->
            if (credential.type() == EmailCredential.TYPE) {
                credential as EmailCredential
                handleTask(firebaseAuth.createUserWithEmailAndPassword(credential.email, credential.password), emitter)

            } else {
                handleTask(firebaseAuth.signInWithCredential(mapper.map(credential)), emitter)
            }
        })
    }

    override fun signIn(credential: AuthCredential): Single<T> {
        return Single.create({ emitter ->
            handleTask(firebaseAuth.signInWithCredential(mapper.map(credential)), emitter)
        })
    }

    private fun handleTask(task: Task<AuthResult>, emitter: SingleEmitter<T>) {
        task.addOnCompleteListener(backgroundExecutor, OnCompleteListener {
            if (!it.isSuccessful) {
                emitter.onError(it.exception!!)
                return@OnCompleteListener
            }
            emitter.onSuccess(userFactory.createUser(it.result.user))
        })
    }

    override fun signOut(): Single<T> {
        val currentUser = firebaseAuth.currentUser
                ?: return Single.error(IllegalStateException("User is not signed in!"))
        val user = userFactory.createUser(currentUser)
        return Single.create({ emitter ->
            firebaseAuth.addAuthStateListener(object : FirebaseAuth.AuthStateListener {
                override fun onAuthStateChanged(firebaseAuth: FirebaseAuth) {
                    if (firebaseAuth.currentUser == null) {
                        emitter.onSuccess(user)
                        firebaseAuth.removeAuthStateListener(this)
                    }
                }
            })
            firebaseAuth.signOut()
        })
    }

}