Rails.application.routes.draw do
  match 'select_course', to: 'courses#select', via: :post

  resources :chapters do
    member do
      post 'activate'
      post 'deactivate'
      post 'moveup'
      post 'movedown'
    end

    resources :sections do
      member do
        post 'activate'
        post 'deactivate'
        post 'moveup'
        post 'movedown'
        get 'preview'
      end
      resources :subsections, only: [:destroy]
    end
  end

  root to: 'home#index'
end
