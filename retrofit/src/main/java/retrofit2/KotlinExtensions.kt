/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("KotlinExtensions")
@file:Suppress("BlockingMethodInNonBlockingContext")

package retrofit2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

inline fun <reified T> Retrofit.create(): T = create(T::class.java)

suspend fun <T : Any> Call<T>.await(): T {
  val response = executeAsync()
  val isSuccessful = response.isSuccessful
  val body = response.body()

  when {
    isSuccessful && body != null -> return body
    !isSuccessful                -> throw HttpException(response)
    else                         -> {
      val invocation = request().tag(Invocation::class.java)!!
      val method = invocation.method()
      val nullResponseMessage = "Response from ${method.declaringClass.name}.${method.name} was null but response body type was declared as non-null"
      throw KotlinNullPointerException(nullResponseMessage)
    }
  }
}

@JvmName("awaitNullable")
suspend fun <T : Any> Call<T?>.await(): T? {
  this.execute()
  val response = executeAsync()
  val body = response.body()

  when {
    response.isSuccessful -> throw HttpException(response)
    else                  -> return body
  }
}

suspend fun <T : Any> Call<T>.awaitResponse(): Response<T> = executeAsync()

private suspend fun <T> Call<T>.executeAsync(): Response<T> {
  val call = this
  return suspendCancellableCoroutine { continuation ->

    continuation.invokeOnCancellation { call.cancel() }

    CoroutineScope(continuation.context).launch {
      val result = runCatching(call::execute)
      continuation.resumeWith(result)
    }
  }
}