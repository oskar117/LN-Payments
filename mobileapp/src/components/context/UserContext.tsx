/* eslint-disable @typescript-eslint/no-empty-function */

import React, { createContext, useCallback, useState } from 'react'

interface UserI {
  email: string | null
  token: string | null
  privateKey: Buffer | null
  publicKey: Buffer | null
  uploadKeys: boolean | null
}

export const EMPTY_USER = Object.freeze({
  email: null,
  token: null,
  privateKey: null,
  publicKey: null,
  uploadKeys: false,
})

export const UserContext = createContext<{
  user: UserI
  setUser: (user: UserI) => void
  updateUser: (params: Partial<UserI>) => void
}>({ user: EMPTY_USER, setUser: () => {}, updateUser: () => {} })

export const UserContextProvider: React.FC = ({ children }) => {
  const [user, setUser] = useState<UserI>(EMPTY_USER)
  const updateUser = useCallback(
    (params: Partial<UserI>) => {
      setUser((prevUser) => ({ ...prevUser, ...params }))
    },
    [setUser],
  )

  return (
    <UserContext.Provider
      value={{
        user,
        setUser,
        updateUser,
      }}
    >
      {children}
    </UserContext.Provider>
  )
}
