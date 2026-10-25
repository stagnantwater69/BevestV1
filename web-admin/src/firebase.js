import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyBJMgiehdzS4V_La39159w3NZ5yeeTIwYU",
  authDomain: "bevest-70698.firebaseapp.com",
  databaseURL: "https://bevest-70698-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "bevest-70698",
  storageBucket: "bevest-70698.firebasestorage.app",
  messagingSenderId: "605475638218",
  appId: "1:605475638218:web:5acba3a22067b768405d33",
  measurementId: "G-EF6JPS6L9W"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);

// Secondary app instance for creating new users without signing out the admin
export const secondaryApp = initializeApp(firebaseConfig, "Secondary");
export const secondaryAuth = getAuth(secondaryApp);
