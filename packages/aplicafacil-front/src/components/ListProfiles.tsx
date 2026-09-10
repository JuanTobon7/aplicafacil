import { Plus, Trash2 } from 'lucide-react';
import type { ProfileResponse } from '../types/profile';

interface ProfilesComponentProps {
  profileBank: ProfileResponse[];
  onSelectProfile: (profileId: string) => void;
  onDeleteProfile: (profileId: string) => void;
  onCreateProfile?: () => void;
  deletingProfileId?: string | null;
}

function ProfileItem({
  profile,
  onSelect,
  onDelete,
  isDeleting,
}: {
  profile: ProfileResponse;
  onSelect: (profileId: string) => void;
  onDelete: (profileId: string) => void;
  isDeleting: boolean;
}) {
  const handleDelete = () => {
    const confirmed = window.confirm(`¿Eliminar "${profile.title}"? Esta acción no se puede deshacer.`);
    if (confirmed) onDelete(profile.id);
  };

  return (
    <li className="group relative rounded-xl border border-gray-200 bg-white transition-all hover:border-blue-500 hover:shadow-md">
      <button
        type="button"
        onClick={() => onSelect(profile.id)}
        className="w-full rounded-xl p-4 pr-14 text-left transition-colors hover:bg-blue-50"
      >
        <div className="min-w-0">
          <h3 className="truncate text-lg font-semibold text-gray-900">
            {profile.title}
          </h3>

          <p className="mt-2 line-clamp-3 text-sm text-gray-600">
            {profile.summary}
          </p>
        </div>

        <span className="mt-3 inline-block rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-700">
          Abrir
        </span>
      </button>

      <button
        type="button"
        onClick={handleDelete}
        disabled={isDeleting}
        aria-label={`Eliminar ${profile.title}`}
        className="absolute right-3 top-3 flex h-9 w-9 items-center justify-center rounded-full
                   text-gray-400 transition-colors
                   hover:bg-red-50 hover:text-red-600
                   disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Trash2 size={17} />
      </button>
    </li>
  );
}

export function ProfilesList({
  profileBank,
  onSelectProfile,
  onDeleteProfile,
  onCreateProfile,
  deletingProfileId,
}: ProfilesComponentProps) {
  return (
    <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
      <div className="mb-5 flex items-center justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-gray-900">
            Perfiles guardados
          </h2>

          <p className="mt-1 text-sm text-gray-500">
            Selecciona un perfil para cargar su información.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <span className="rounded-full bg-gray-100 px-3 py-1 text-sm font-medium text-gray-700">
            {profileBank.length}
          </span>

          {onCreateProfile && (
            <button
              type="button"
              onClick={onCreateProfile}
              className="flex items-center gap-1.5 rounded-full bg-blue-600 px-3.5 py-1.5
                         text-sm font-medium text-white transition-colors hover:bg-blue-700"
            >
              <Plus size={16} />
              Nuevo perfil
            </button>
          )}
        </div>
      </div>

      {profileBank.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 py-10 text-center">
          <p className="text-gray-500">
            Aún no tienes perfiles guardados.
          </p>
        </div>
      ) : (
        <ul className="space-y-3">
          {profileBank.map((profile) => (
            <ProfileItem
              key={profile.id}
              profile={profile}
              onSelect={onSelectProfile}
              onDelete={onDeleteProfile}
              isDeleting={deletingProfileId === profile.id}
            />
          ))}
        </ul>
      )}
    </section>
  );
}